package com.anninh.bookings.controller;

import com.anninh.bookings.model.Booking;
import com.anninh.bookings.repo.BookingRepository;
import com.anninh.bookings.service.MomoPaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final MomoPaymentService momoPaymentService;
    private final BookingRepository bookingRepository;

    public PaymentController(MomoPaymentService momoPaymentService, BookingRepository bookingRepository) {
        this.momoPaymentService = momoPaymentService;
        this.bookingRepository = bookingRepository;
    }

    @PostMapping("/momo")
    public ResponseEntity<?> momoPayment(@RequestBody Map<String, Object> body) {
        try {
            String externalBookingId = body.get("bookingId").toString();
            String amount = body.get("amount").toString();

            // Find the booking by external bookingId to get internal ID
            Booking booking = bookingRepository.findByBookingId(externalBookingId);
            if (booking == null) {
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "detail", "Booking not found"
                ));
            }

            String description = "Booking " + externalBookingId;

            Map<String, Object> result = momoPaymentService.createPayment(booking.getId(), amount, description);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "detail", "MOMO payment failed: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/momo/callback")
    public ResponseEntity<?> momoCallback(@RequestBody Map<String, Object> callback) {
        try {
            momoPaymentService.handleMomoCallback(callback);
            // Return response for MOMO IPN
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "IPN received"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "detail", "Callback processing failed: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/momo/status/{orderId}")
    public ResponseEntity<?> getMomoStatus(@PathVariable String orderId) {
        Map<String, Object> status = momoPaymentService.getOrderStatus(orderId);
        return ResponseEntity.ok(status);
    }
}
