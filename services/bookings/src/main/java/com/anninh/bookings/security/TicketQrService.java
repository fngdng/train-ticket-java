package com.anninh.bookings.security;

import com.anninh.bookings.model.Booking;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class TicketQrService {
    private final TicketKeyService ticketKeyService;

    @Value("${ticket.signature.issuer:train-ticket-java}")
    private String issuer;

    public TicketQrService(TicketKeyService ticketKeyService) {
        this.ticketKeyService = ticketKeyService;
    }

    public String issueTicketToken(Booking booking) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("bookingId", booking.getBookingId());
        claims.put("userId", booking.getUserId());
        claims.put("train", booking.getTrain());
        claims.put("origin", booking.getOrigin());
        claims.put("destination", booking.getDestination());
        claims.put("departure", booking.getDeparture());
        claims.put("arrival", booking.getArrival());
        claims.put("price", booking.getPrice());
        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject(booking.getBookingId())
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plusSeconds(60L * 60L * 24L * 30L)))
                .addClaims(claims)
                .signWith(ticketKeyService.getPrivateKey(), SignatureAlgorithm.RS256)
                .compact();
    }

    public Map<String, Object> verifyTicketToken(String token) {
        var claims = Jwts.parserBuilder()
                .setSigningKey(ticketKeyService.getPublicKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        Map<String, Object> payload = new HashMap<>();
        payload.put("valid", true);
        payload.put("bookingId", claims.get("bookingId"));
        payload.put("userId", claims.get("userId"));
        payload.put("train", claims.get("train"));
        payload.put("origin", claims.get("origin"));
        payload.put("destination", claims.get("destination"));
        payload.put("departure", claims.get("departure"));
        payload.put("arrival", claims.get("arrival"));
        payload.put("price", claims.get("price"));
        payload.put("issuedAt", claims.getIssuedAt());
        payload.put("expiresAt", claims.getExpiration());
        return payload;
    }

    public String getPublicKeyBase64() {
        return java.util.Base64.getEncoder().encodeToString(ticketKeyService.getPublicKey().getEncoded());
    }
}