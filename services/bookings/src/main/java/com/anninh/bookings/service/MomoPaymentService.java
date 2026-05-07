package com.anninh.bookings.service;

import com.anninh.bookings.model.MomoTransaction;
import com.anninh.bookings.repo.MomoTransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.Base64;

@Service
public class MomoPaymentService {

    @Value("${momo.partner-code}")
    private String partnerCode;

    @Value("${momo.access-key}")
    private String accessKey;

    @Value("${momo.secret-key}")
    private String secretKey;

    @Value("${momo.endpoint}")
    private String endpoint;

    @Value("${momo.return-url}")
    private String returnUrl;

    @Value("${momo.notify-url}")
    private String notifyUrl;

    @Value("${momo.test-mode:false}")
    private boolean testMode;

    @Value("${momo.test-pay-url-base:http://localhost:8081/booking.html?mockPayment=success&orderId=}")
    private String testPayUrlBase;

    private final MomoTransactionRepository momoRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public MomoPaymentService(MomoTransactionRepository momoRepository, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.momoRepository = momoRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Create MOMO payment link
     */
    public Map<String, Object> createPayment(Long bookingId, String amount, String description) throws Exception {
        String orderId = "MOMO_" + bookingId + "_" + System.currentTimeMillis();
        String requestId = String.valueOf(System.currentTimeMillis());

        // TEST MODE: Return fake success without calling MOMO API
        if (testMode) {
            String mockPayUrl = testPayUrlBase + orderId;
            MomoTransaction transaction = new MomoTransaction(bookingId, orderId, Long.parseLong(amount), requestId);
            transaction.setPayUrl(mockPayUrl);
            transaction.setQrCodeUrl(mockPayUrl);
            transaction.setStatus("PENDING");
            momoRepository.save(transaction);

            return Map.of(
                    "success", true,
                    "provider", "MOMO",
                    "orderId", orderId,
                    "requestId", requestId,
                    "amount", amount,
                    "payUrl", mockPayUrl,
                    "qrCodeUrl", mockPayUrl,
                    "status", "PENDING",
                    "message", "TEST MODE - No real charge"
            );
        }

        // Build request data
        Map<String, String> requestData = new LinkedHashMap<>();
        requestData.put("partnerCode", partnerCode);
        requestData.put("partnerName", "TrainTicket");
        requestData.put("requestId", requestId);
        requestData.put("amount", amount);
        requestData.put("orderId", orderId);
        requestData.put("orderInfo", description);
        requestData.put("redirectUrl", returnUrl);
        requestData.put("ipnUrl", notifyUrl);
        requestData.put("requestType", "captureMoMoWallet");
        requestData.put("autoCapture", "true");
        requestData.put("lang", "vi");
        requestData.put("extraData", "");

        // Generate signature
        String signature = generateSignature(requestData);
        requestData.put("signature", signature);

        // Save transaction record
        MomoTransaction transaction = new MomoTransaction(bookingId, orderId, Long.parseLong(amount), requestId);
        momoRepository.save(transaction);

        try {
            // Call MOMO API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String requestBody = objectMapper.writeValueAsString(requestData);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(endpoint, entity, Map.class);

            if (response != null && "0".equals(String.valueOf(response.get("resultCode")))) {
                // Success - update transaction with payment URL
                String payUrl = (String) response.get("payUrl");
                String qrCodeUrl = (String) response.get("qrCodeUrl");

                transaction.setPayUrl(payUrl);
                transaction.setQrCodeUrl(qrCodeUrl);
                transaction.setStatus("PENDING");
                momoRepository.save(transaction);

                return Map.of(
                        "success", true,
                        "provider", "MOMO",
                        "orderId", orderId,
                        "requestId", requestId,
                        "amount", amount,
                        "payUrl", payUrl,
                        "qrCodeUrl", qrCodeUrl,
                        "status", "PENDING"
                );
            } else {
                // MOMO API error
                String resultMessage = (String) response.getOrDefault("message", "Unknown error");
                transaction.setStatus("FAILED");
                transaction.setResultMessage(resultMessage);
                momoRepository.save(transaction);

                return Map.of(
                        "success", false,
                        "detail", "MOMO payment creation failed: " + resultMessage
                );
            }
        } catch (Exception e) {
            transaction.setStatus("ERROR");
            transaction.setResultMessage(e.getMessage());
            momoRepository.save(transaction);

            return Map.of(
                    "success", false,
                    "detail", "MOMO payment service error: " + e.getMessage()
            );
        }
    }

    /**
     * Verify MOMO IPN callback signature
     */
    public boolean verifySignature(Map<String, String> data, String signature) throws Exception {
        String signData = generateRawSignatureData(data);
        String computedSignature = generateHmacSHA256(signData, secretKey);
        return computedSignature.equals(signature);
    }

    /**
     * Handle MOMO IPN callback
     */
    public void handleMomoCallback(Map<String, Object> callback) {
        String orderId = (String) callback.get("orderId");
        Integer resultCode = Integer.parseInt(String.valueOf(callback.get("resultCode")));
        String resultMessage = (String) callback.get("message");
        Long transId = Long.parseLong(String.valueOf(callback.getOrDefault("transId", 0)));

        Optional<MomoTransaction> optionalTransaction = momoRepository.findByOrderId(orderId);
        if (optionalTransaction.isPresent()) {
            MomoTransaction transaction = optionalTransaction.get();
            transaction.setResultCode(resultCode);
            transaction.setResultMessage(resultMessage);
            transaction.setTransId(transId);

            if (resultCode == 0) {
                transaction.setStatus("COMPLETED");
            } else {
                transaction.setStatus("FAILED");
            }

            momoRepository.save(transaction);
        }
    }

    /**
     * Get order status from database (after IPN update)
     */
    public Map<String, Object> getOrderStatus(String orderId) {
        Optional<MomoTransaction> transaction = momoRepository.findByOrderId(orderId);
        if (transaction.isPresent()) {
            MomoTransaction t = transaction.get();
            return Map.of(
                    "orderId", orderId,
                    "status", t.getStatus(),
                    "resultCode", t.getResultCode() != null ? t.getResultCode() : -1,
                    "resultMessage", t.getResultMessage() != null ? t.getResultMessage() : "",
                    "bookingId", t.getBookingId()
            );
        }
        return Map.of("success", false, "detail", "Order not found");
    }

    /**
     * Generate MOMO signature
     */
    private String generateSignature(Map<String, String> data) throws Exception {
        String rawSignature = generateRawSignatureData(data);
        return generateHmacSHA256(rawSignature, secretKey);
    }

    /**
     * Build raw signature data in MOMO format
     */
    private String generateRawSignatureData(Map<String, String> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("partnerCode=").append(data.getOrDefault("partnerCode", ""));
        sb.append("&accessKey=").append(accessKey);
        sb.append("&requestId=").append(data.getOrDefault("requestId", ""));
        sb.append("&amount=").append(data.getOrDefault("amount", ""));
        sb.append("&orderId=").append(data.getOrDefault("orderId", ""));
        sb.append("&orderInfo=").append(data.getOrDefault("orderInfo", ""));
        sb.append("&returnUrl=").append(data.getOrDefault("returnUrl", ""));
        sb.append("&notifyUrl=").append(data.getOrDefault("notifyUrl", ""));
        sb.append("&extraData=").append(data.getOrDefault("extraData", ""));
        return sb.toString();
    }

    /**
     * Generate HMAC SHA-256 signature
     */
    private String generateHmacSHA256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
