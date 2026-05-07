package com.anninh.bookings.controller;

import com.anninh.bookings.model.Booking;
import com.anninh.bookings.repo.BookingRepository;
import com.anninh.bookings.security.AuthClient;
import com.anninh.bookings.security.TicketQrService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/tickets")
public class TicketController {
    private final BookingRepository bookingRepository;
    private final AuthClient authClient;
    private final TicketQrService ticketQrService;

    public TicketController(BookingRepository bookingRepository, AuthClient authClient, TicketQrService ticketQrService) {
        this.bookingRepository = bookingRepository;
        this.authClient = authClient;
        this.ticketQrService = ticketQrService;
    }

    @GetMapping("/key")
    public ResponseEntity<?> publicKey() {
        return ResponseEntity.ok(Map.of(
                "algorithm", "RSA",
                "format", "X.509",
                "key", ticketQrService.getPublicKeyBase64()
        ));
    }

    @GetMapping("/my")
    public ResponseEntity<?> myTickets(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = authClient.verify(authorization);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("detail", "unauthorized"));
        }
        String userId = String.valueOf(user.getOrDefault("id", ""));
        List<Booking> bookings = bookingRepository.findByUserId(userId);

        // Backfill token for old records so every ticket can render a scannable QR.
        List<Booking> dirty = new ArrayList<>();
        for (Booking booking : bookings) {
            if (booking.getTicketToken() == null || booking.getTicketToken().isBlank()) {
                booking.setTicketToken(ticketQrService.issueTicketToken(booking));
                dirty.add(booking);
            }
        }
        if (!dirty.isEmpty()) {
            bookingRepository.saveAll(dirty);
        }

        return ResponseEntity.ok(bookings.stream().map(this::ticketView).toList());
    }

    @GetMapping("/{bookingId}/qr")
    public ResponseEntity<?> qr(@RequestHeader(value = "Authorization", required = false) String authorization,
                                @PathVariable String bookingId) {
        Map<String, Object> user = authClient.verify(authorization);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("detail", "unauthorized"));
        }
        Booking booking = bookingRepository.findByBookingId(bookingId);
        if (booking == null) {
            return ResponseEntity.status(404).body(Map.of("detail", "ticket not found"));
        }
        String userId = String.valueOf(user.getOrDefault("id", ""));
        String role = String.valueOf(user.getOrDefault("role", ""));
        if (!userId.equals(String.valueOf(booking.getUserId())) && !"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(403).body(Map.of("detail", "forbidden"));
        }
        if (booking.getTicketToken() == null || booking.getTicketToken().isBlank()) {
            booking.setTicketToken(ticketQrService.issueTicketToken(booking));
            bookingRepository.save(booking);
        }
        return ResponseEntity.ok(ticketView(booking));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "token required"));
        }
        try {
            return ResponseEntity.ok(ticketQrService.verifyTicketToken(token));
        } catch (Exception ex) {
            return ResponseEntity.status(400).body(Map.of("valid", false, "detail", "invalid or tampered ticket"));
        }
    }

    private Map<String, Object> ticketView(Booking booking) {
        Map<String, Object> ticket = new HashMap<>();
        ticket.put("bookingId", booking.getBookingId());
        ticket.put("train", booking.getTrain());
        ticket.put("origin", booking.getOrigin());
        ticket.put("destination", booking.getDestination());
        ticket.put("departure", booking.getDeparture());
        ticket.put("arrival", booking.getArrival());
        ticket.put("price", booking.getPrice());
        ticket.put("userId", booking.getUserId());
        ticket.put("passengerName", booking.getPassengerName());
        ticket.put("passengerPhone", booking.getPassengerPhone());
        ticket.put("passengerEmail", booking.getPassengerEmail());
        ticket.put("paymentMethod", booking.getPaymentMethod());
        ticket.put("paymentStatus", booking.getPaymentStatus());
        ticket.put("ticketToken", booking.getTicketToken());
        ticket.put("ticketQrUrl", booking.getTicketToken() == null || booking.getTicketToken().isBlank()
                ? ""
                : buildQrUrl(booking.getTicketToken()));
        return ticket;
    }

    private String buildQrUrl(String token) {
        return "https://api.qrserver.com/v1/create-qr-code/?size=220x220&data="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}