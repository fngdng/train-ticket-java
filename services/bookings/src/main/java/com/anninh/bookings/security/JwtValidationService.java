package com.anninh.bookings.security;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

@Service
public class JwtValidationService {
    private final RestTemplate restTemplate;
    
    @Value("${auth.service.url:http://auth-service:8080}")
    private String authServiceUrl;
    
    private PublicKey cachedPublicKey;

    public JwtValidationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void init() {
        try {
            refreshPublicKey();
        } catch (Exception e) {
            System.err.println("Warning: Failed to fetch public key on startup: " + e.getMessage());
            // Continue anyway; we'll try again on first token validation
        }
    }

    /**
     * Fetch and cache the public key from auth-service
     */
    public void refreshPublicKey() throws Exception {
        try {
            Map response = restTemplate.getForObject(authServiceUrl + "/auth/key", Map.class);
            if (response == null || !response.containsKey("key")) {
                throw new Exception("Invalid key response from auth-service");
            }
            String encodedKey = (String) response.get("key");
            byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            this.cachedPublicKey = kf.generatePublic(spec);
        } catch (Exception e) {
            throw new Exception("Failed to load public key from auth service: " + e.getMessage(), e);
        }
    }

    /**
     * Validate JWT token using cached public key
     */
    public Map<String, Object> validateToken(String token) throws Exception {
        if (cachedPublicKey == null) {
            refreshPublicKey();
        }
        
        try {
            var claims = Jwts.parserBuilder()
                    .setSigningKey(cachedPublicKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            return Map.of(
                "valid", true,
                "username", claims.getSubject(),
                "role", claims.get("role", String.class)
            );
        } catch (Exception e) {
            // If token validation fails, try refreshing the key once
            try {
                refreshPublicKey();
                var claims = Jwts.parserBuilder()
                        .setSigningKey(cachedPublicKey)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();
                return Map.of(
                    "valid", true,
                    "username", claims.getSubject(),
                    "role", claims.get("role", String.class)
                );
            } catch (Exception ex) {
                throw new Exception("Token validation failed: " + ex.getMessage(), ex);
            }
        }
    }
}
