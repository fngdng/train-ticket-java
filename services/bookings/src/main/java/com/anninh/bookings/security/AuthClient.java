package com.anninh.bookings.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class AuthClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${auth.service.url:http://auth-service:8080}")
    private String authServiceUrl;

    @SuppressWarnings("unchecked")
    public Map<String, Object> verify(String authorizationHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authorizationHeader);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map> res = restTemplate.exchange(
                    authServiceUrl + "/auth/verify",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
                return null;
            }
            Object user = res.getBody().get("user");
            if (user instanceof Map<?, ?> userMap) {
                return (Map<String, Object>) userMap;
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }
}
