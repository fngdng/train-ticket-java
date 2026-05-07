package com.anninh.auth.service;

import com.anninh.auth.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnBean(JavaMailSender.class)
public class TwoFactorSenderService {
    private final Optional<JavaMailSender> mailSender;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${twilio.from-number:}")
    private String twilioFromNumber;

    @Value("${mail.from:}")
    private String mailFrom;

    public TwoFactorSenderService(Optional<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    public void sendCode(User user, String channel, String destination, String code) {
        if ("sms".equalsIgnoreCase(channel)) {
            sendSms(destination, code);
            return;
        }
        sendEmail(destination, code, user);
    }

    private void sendEmail(String destination, String code, User user) {
        if (destination == null || destination.isBlank()) {
            throw new IllegalStateException("Email destination is missing");
        }
        if (mailSender.isEmpty()) {
            throw new IllegalStateException("SMTP is not configured");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(destination);
        message.setSubject("Ma xac minh 2 lop");
        message.setText("Xin chao " + (user.getFullName() == null ? user.getUsername() : user.getFullName()) + ", ma xac minh cua ban la: " + code + " . Ma co hieu luc 5 phut.");
        if (mailFrom != null && !mailFrom.isBlank()) {
            message.setFrom(mailFrom);
        }
        mailSender.ifPresent(sender -> sender.send(message));
    }

    private void sendSms(String destination, String code) {
        if (destination == null || destination.isBlank()) {
            throw new IllegalStateException("Phone destination is missing");
        }
        if (twilioAccountSid == null || twilioAccountSid.isBlank() || twilioAuthToken == null || twilioAuthToken.isBlank() || twilioFromNumber == null || twilioFromNumber.isBlank()) {
            throw new IllegalStateException("SMS provider is not configured");
        }
        String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilioAccountSid + "/Messages.json";
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(twilioAccountSid, twilioAuthToken);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String body = "To=" + destination + "&From=" + twilioFromNumber + "&Body=" + java.net.URLEncoder.encode("Ma xac minh 2 lop: " + code, java.nio.charset.StandardCharsets.UTF_8);
        restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
    }
}