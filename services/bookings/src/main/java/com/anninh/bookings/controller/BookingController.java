package com.anninh.bookings.controller;

import com.anninh.bookings.model.Booking;
import com.anninh.bookings.repo.BookingRepository;
import com.anninh.bookings.security.AuthClient;
import com.anninh.bookings.security.TicketQrService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingRepository repo;
    private final AuthClient authClient;
    private final TicketQrService ticketQrService;

    public BookingController(BookingRepository repo, AuthClient authClient, TicketQrService ticketQrService){
        this.repo = repo;
        this.authClient = authClient;
        this.ticketQrService = ticketQrService;
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("detail", "unauthorized"));
    }

    private Map<String, Object> verifiedUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authClient.verify(authorization);
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String,Object> payload
    ){
        Map<String, Object> user = verifiedUser(authorization);
        if (user == null) {
            return unauthorized();
        }

        Booking b = new Booking();
        b.setBookingId(UUID.randomUUID().toString());
        b.setTrain((String)payload.get("train"));
        b.setOrigin((String)payload.get("from"));
        b.setDestination((String)payload.get("to"));
        b.setDeparture((String)payload.get("departure"));
        b.setArrival((String)payload.get("arrival"));
        Object p = payload.get("price");
        if(p instanceof Number) b.setPrice(((Number) p).longValue());
        else if(p!=null) try{ b.setPrice(Long.parseLong(p.toString())); }catch(Exception ignored){}
        b.setUserId(String.valueOf(user.getOrDefault("id", "")));
        b.setPassengerName((String) payload.getOrDefault("passengerName", ""));
        b.setPassengerPhone((String) payload.getOrDefault("passengerPhone", ""));
        b.setPassengerEmail((String) payload.getOrDefault("passengerEmail", ""));
        b.setPaymentMethod((String) payload.getOrDefault("paymentMethod", ""));
        b.setPaymentStatus((String) payload.getOrDefault("paymentStatus", "PENDING"));

        Booking saved = repo.save(b);
        saved.setTicketToken(ticketQrService.issueTicketToken(saved));
        repo.save(saved);
        return ResponseEntity.ok(Map.of(
            "bookingId", saved.getBookingId(),
            "status","created",
            "paymentStatus", saved.getPaymentStatus(),
            "ticketToken", saved.getTicketToken(),
            "ticketQrUrl", "https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=" + saved.getTicketToken()
        ));
    }

    @GetMapping({"", "/"})
    public ResponseEntity<?> list(@RequestHeader(value = "Authorization", required = false) String authorization){
        Map<String, Object> user = verifiedUser(authorization);
        if (user == null) {
            return unauthorized();
        }
        String role = String.valueOf(user.getOrDefault("role", ""));
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(403).body(Map.of("detail", "forbidden"));
        }
        return ResponseEntity.ok(repo.findAll());
    }

    @GetMapping("/my")
    public ResponseEntity<?> myBookings(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = verifiedUser(authorization);
        if (user == null) {
            return unauthorized();
        }
        String userId = String.valueOf(user.getOrDefault("id", ""));
        return ResponseEntity.ok(repo.findByUserId(userId));
    }
}
