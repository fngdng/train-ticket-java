package com.anninh.auth.controller;

import com.anninh.auth.model.TwoFactorChallenge;
import com.anninh.auth.model.User;
import com.anninh.auth.repo.TwoFactorChallengeRepository;
import com.anninh.auth.repo.UserRepository;
import com.anninh.auth.service.JwtService;
import com.anninh.auth.service.TwoFactorSenderService;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserRepository userRepository;
    private final TwoFactorChallengeRepository twoFactorChallengeRepository;
    private final Optional<TwoFactorSenderService> twoFactorSenderService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService;
    private final Random random = new Random();
    private final Map<String, Deque<Map<String, Object>>> twoFactorInbox = new ConcurrentHashMap<>();
    private static final List<String> SUPPORTED_2FA_CHANNELS = List.of("sms", "email", "passkey", "totp");

    @Value("${bootstrap.admin.secret:}")
    private String bootstrapSecret;

    @Value("${twofactor.demo-mode:true}")
    private boolean twoFactorDemoMode;

    private boolean bootstrapUsed = false;

    public AuthController(UserRepository userRepository,
                          TwoFactorChallengeRepository twoFactorChallengeRepository,
                          Optional<TwoFactorSenderService> twoFactorSenderService,
                          JwtService jwtService) {
        this.userRepository = userRepository;
        this.twoFactorChallengeRepository = twoFactorChallengeRepository;
        this.twoFactorSenderService = twoFactorSenderService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String fullName = body.getOrDefault("fullName", "");
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("detail","username and password required"));
        }
        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("detail","username exists"));
        }
        String hashed = passwordEncoder.encode(password);
        User u = new User(username, hashed, fullName);
        User saved = userRepository.save(u);
        String token = jwtService.generateToken(username, u.getRole(), saved.getId());
        return ResponseEntity.ok(Map.of(
            "token", token,
            "user", Map.of(
                "id", saved.getId(),
                "username", saved.getUsername(),
                "role", saved.getRole(),
                "fullName", saved.getFullName() == null ? "" : saved.getFullName()
            )
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String requestedChannel = body.getOrDefault("twoFactorChannel", "");
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("detail","username and password required"));
        }
        var userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("detail","invalid username or password"));
        }
        User u = userOpt.get();
        if (!passwordEncoder.matches(password, u.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("detail","invalid username or password"));
        }
        if (u.isTwoFactorEnabled()) {
            List<String> enabledChannels = getEnabledTwoFactorChannels(u);
            if (enabledChannels.isEmpty()) {
                return ResponseEntity.status(400).body(Map.of("detail", "No available 2FA method. Please configure at least one method in account settings."));
            }
            String channel;
            try {
                channel = resolveTwoFactorChannel(u, requestedChannel, enabledChannels, true);
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.status(400).body(Map.of("detail", ex.getMessage(), "availableChannels", enabledChannels));
            }
            if ("sms".equals(channel) || "email".equals(channel)) {
                String destination;
                try {
                    destination = resolveTwoFactorDestination(u, channel);
                } catch (IllegalStateException ex) {
                    return ResponseEntity.badRequest().body(Map.of("detail", ex.getMessage()));
                }
                String code = String.format("%06d", random.nextInt(1_000_000));
                TwoFactorChallenge challenge = new TwoFactorChallenge();
                challenge.setUsername(u.getUsername());
                challenge.setChannel(channel);
                challenge.setDestination(destination);
                challenge.setCodeHash(passwordEncoder.encode(code));
                challenge.setExpiresAt(LocalDateTime.now().plusMinutes(5));
                twoFactorChallengeRepository.save(challenge);
                twoFactorSenderService.ifPresent(sender -> sender.sendCode(u, channel, destination, code));
                pushDemoTwoFactorInbox(u.getUsername(), channel, destination, code);

                Map<String, Object> pendingResponse = new HashMap<>();
                pendingResponse.put("twoFactorRequired", true);
                pendingResponse.put("channel", channel);
                pendingResponse.put("supportedChannels", enabledChannels);
                pendingResponse.put("message", "Enter your 2FA code to continue");
                if (twoFactorDemoMode) {
                    pendingResponse.put("demoCode", code);
                    pendingResponse.put("demoInboxUrl", "/2fa-inbox.html?username=" + u.getUsername());
                }
                return ResponseEntity.status(202).body(pendingResponse);
            }

            return ResponseEntity.status(202).body(Map.of(
                    "twoFactorRequired", true,
                    "channel", channel,
                    "supportedChannels", enabledChannels,
                    "message", "Enter your 2FA code to continue"
                ));
        }
        String token = jwtService.generateToken(username, u.getRole(), u.getId());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", userPayload(u)
        ));
    }

    @PostMapping("/login/2fa/verify")
    public ResponseEntity<?> verifyTwoFactorLogin(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String code = body.get("code");
        String requestedChannel = body.getOrDefault("twoFactorChannel", "");

        if (username == null || password == null || code == null) {
            return ResponseEntity.badRequest().body(Map.of("detail", "username, password and code required"));
        }

        var userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("detail", "invalid username or password"));
        }
        User u = userOpt.get();
        if (!passwordEncoder.matches(password, u.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("detail", "invalid username or password"));
        }
        if (!u.isTwoFactorEnabled()) {
            return ResponseEntity.status(400).body(Map.of("detail", "2FA is not enabled"));
        }

        List<String> enabledChannels = getEnabledTwoFactorChannels(u);
        if (enabledChannels.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("detail", "No available 2FA method configured"));
        }
        String channel;
        try {
            channel = resolveTwoFactorChannel(u, requestedChannel, enabledChannels, true);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(400).body(Map.of("detail", ex.getMessage(), "availableChannels", enabledChannels));
        }
        if ("totp".equals(channel)) {
            if (u.getTotpSecret() == null || u.getTotpSecret().isBlank() || !verifyTotpCode(u.getTotpSecret(), code)) {
                return ResponseEntity.status(400).body(Map.of("detail", "invalid authenticator code"));
            }
        } else if ("passkey".equals(channel)) {
            if (u.getPasskeyCodeHash() == null || u.getPasskeyCodeHash().isBlank() || !passwordEncoder.matches(code, u.getPasskeyCodeHash())) {
                return ResponseEntity.status(400).body(Map.of("detail", "invalid passkey code"));
            }
        } else {
            var challengeOpt = twoFactorChallengeRepository.findTopByUsernameAndChannelAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(
                    u.getUsername(), channel, LocalDateTime.now());
            if (challengeOpt.isEmpty()) {
                return ResponseEntity.status(400).body(Map.of("detail", "no active 2FA challenge"));
            }
            TwoFactorChallenge challenge = challengeOpt.get();
            if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
                return ResponseEntity.status(400).body(Map.of("detail", "invalid code"));
            }
            challenge.setUsedAt(LocalDateTime.now());
            twoFactorChallengeRepository.save(challenge);
        }

        String token = jwtService.generateToken(username, u.getRole(), u.getId());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", userPayload(u)
        ));
    }

    @PostMapping("/bootstrap-admin")
    public ResponseEntity<?> bootstrapAdmin(@RequestBody Map<String, String> body) {
        String secret = body.get("secret");
        String username = body.get("username");
        String password = body.get("password");
        String fullName = body.getOrDefault("fullName", "");
        
        // One-time use of bootstrap secret
        if (bootstrapUsed || bootstrapSecret == null || bootstrapSecret.isEmpty() || secret == null || !bootstrapSecret.equals(secret)) {
            return ResponseEntity.status(403).body(Map.of("detail", "invalid or expired bootstrap secret"));
        }
        
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("detail", "username and password required"));
        }
        
        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("detail", "username exists"));
        }
        
        String hashed = passwordEncoder.encode(password);
        User u = new User(username, hashed, fullName);
        u.setRole("ADMIN");
        User saved = userRepository.save(u);
        bootstrapUsed = true;
        
        String token = jwtService.generateToken(username, "ADMIN", saved.getId());
        return ResponseEntity.ok(Map.of(
            "message", "Admin user created (one-time secret expires now)",
            "token", token,
            "user", Map.of(
                "id", saved.getId(),
                "username", saved.getUsername(),
                "role", saved.getRole(),
                "fullName", saved.getFullName() == null ? "" : saved.getFullName(),
                "email", saved.getEmail() == null ? "" : saved.getEmail(),
                "phone", saved.getPhone() == null ? "" : saved.getPhone(),
                "twoFactorEnabled", saved.isTwoFactorEnabled(),
                "twoFactorChannel", saved.getTwoFactorChannel(),
                "twoFactorChannels", getConfiguredTwoFactorChannels(saved)
            )
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        User u = resolveUser(authorization);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("detail", "unauthorized"));
        }
        return ResponseEntity.ok(userPayload(u));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @RequestBody Map<String, Object> body) {
        User u = resolveUser(authorization);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("detail", "unauthorized"));
        }
        String fullName = body.get("fullName") == null ? u.getFullName() : String.valueOf(body.get("fullName"));
        String email = body.get("email") == null ? u.getEmail() : String.valueOf(body.get("email"));
        String phone = body.get("phone") == null ? u.getPhone() : String.valueOf(body.get("phone"));
        String channel = body.get("twoFactorChannel") == null ? u.getTwoFactorChannel() : String.valueOf(body.get("twoFactorChannel"));

        u.setFullName(fullName);
        u.setEmail(email);
        u.setPhone(phone);
        if (body.containsKey("twoFactorChannels")) {
            Object rawChannels = body.get("twoFactorChannels");
            List<String> normalizedChannels = new ArrayList<>();
            if (rawChannels instanceof List<?> listValue) {
                for (Object item : listValue) {
                    if (item == null) {
                        continue;
                    }
                    String normalized = String.valueOf(item).trim().toLowerCase();
                    if (SUPPORTED_2FA_CHANNELS.contains(normalized) && !normalizedChannels.contains(normalized)) {
                        normalizedChannels.add(normalized);
                    }
                }
            } else if (rawChannels != null) {
                String[] parts = String.valueOf(rawChannels).split(",");
                for (String part : parts) {
                    String normalized = part == null ? "" : part.trim().toLowerCase();
                    if (SUPPORTED_2FA_CHANNELS.contains(normalized) && !normalizedChannels.contains(normalized)) {
                        normalizedChannels.add(normalized);
                    }
                }
            }
            if (!normalizedChannels.isEmpty()) {
                u.setTwoFactorChannels(String.join(",", normalizedChannels));
            }
        }
        if (channel != null && !channel.isBlank()) {
            ensureChannelConfigured(u, channel.toLowerCase());
        }
        if (body.containsKey("twoFactorEnabled")) {
            Object enabledObj = body.get("twoFactorEnabled");
            boolean enabled = enabledObj instanceof Boolean
                    ? (Boolean) enabledObj
                    : Boolean.parseBoolean(String.valueOf(enabledObj));
            u.setTwoFactorEnabled(enabled);
        }
        userRepository.save(u);
        return ResponseEntity.ok(userPayload(u));
    }

    @PostMapping("/password/change")
    public ResponseEntity<?> changePassword(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @RequestBody Map<String, String> body) {
        User u = resolveUser(authorization);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("detail", "unauthorized"));
        }
        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");
        if (currentPassword == null || newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("detail", "currentPassword and newPassword required"));
        }
        if (!passwordEncoder.matches(currentPassword, u.getPassword())) {
            return ResponseEntity.status(400).body(Map.of("detail", "current password invalid"));
        }
        u.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(u);
        return ResponseEntity.ok(Map.of("message", "password updated"));
    }

    @PostMapping("/2fa/request")
    public ResponseEntity<?> requestTwoFactor(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @RequestBody(required = false) Map<String, String> body) {
        User u = resolveUser(authorization);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("detail", "unauthorized"));
        }
        String requestedChannel = body == null ? "" : body.getOrDefault("channel", "");
        String channel;
        try {
            channel = resolveTwoFactorChannel(u, requestedChannel, getConfiguredTwoFactorChannels(u), true);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(400).body(Map.of("detail", ex.getMessage(), "availableChannels", getConfiguredTwoFactorChannels(u)));
        }
        if ("totp".equals(channel)) {
            return ResponseEntity.ok(Map.of(
                    "message", "Use your authenticator app code",
                    "channel", channel,
                    "expiresAt", Instant.now().plusSeconds(30).toString()
            ));
        }
        if ("passkey".equals(channel)) {
            return ResponseEntity.ok(Map.of(
                    "message", "Enter your passkey code",
                    "channel", channel,
                    "expiresAt", Instant.now().plusSeconds(300).toString()
            ));
        }

        String destination;
        try {
            destination = resolveTwoFactorDestination(u, channel);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("detail", ex.getMessage()));
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        TwoFactorChallenge challenge = new TwoFactorChallenge();
        challenge.setUsername(u.getUsername());
        challenge.setChannel(channel);
        challenge.setDestination(destination);
        challenge.setCodeHash(passwordEncoder.encode(code));
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        twoFactorChallengeRepository.save(challenge);
        twoFactorSenderService.ifPresent(sender -> sender.sendCode(u, channel, destination, code));
        pushDemoTwoFactorInbox(u.getUsername(), channel, destination, code);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "2FA code sent");
        response.put("channel", channel);
        response.put("expiresAt", Instant.now().plusSeconds(300).toString());
        if (twoFactorDemoMode) {
            response.put("demoCode", code);
            response.put("demoInboxUrl", "/2fa-inbox.html?username=" + u.getUsername());
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/2fa/inbox")
    public ResponseEntity<?> getTwoFactorInbox(@RequestParam(required = false) String username) {
        if (!twoFactorDemoMode) {
            return ResponseEntity.status(403).body(Map.of("detail", "2FA demo inbox is disabled"));
        }
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "username is required"));
        }

        String key = username.trim().toLowerCase();
        List<Map<String, Object>> messages = new ArrayList<>(twoFactorInbox.getOrDefault(key, new ConcurrentLinkedDeque<>()));
        return ResponseEntity.ok(Map.of(
                "demoMode", true,
                "username", username.trim(),
                "count", messages.size(),
                "messages", messages
        ));
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<?> verifyTwoFactor(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             @RequestBody Map<String, String> body) {
        User u = resolveUser(authorization);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("detail", "unauthorized"));
        }
        String code = body.get("code");
        String requestedChannel = body.getOrDefault("channel", "");
        if (code == null) {
            return ResponseEntity.badRequest().body(Map.of("detail", "code required"));
        }
        String channel;
        try {
            channel = resolveTwoFactorChannel(u, requestedChannel, getConfiguredTwoFactorChannels(u), true);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(400).body(Map.of("detail", ex.getMessage(), "availableChannels", getConfiguredTwoFactorChannels(u)));
        }
        try {
            verifyChannelCodeOrThrow(u, channel, code);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(400).body(Map.of("detail", ex.getMessage()));
        }
        u.setTwoFactorEnabled(true);
        ensureChannelConfigured(u, channel);
        userRepository.save(u);
        return ResponseEntity.ok(Map.of("message", "2FA enabled", "user", userPayload(u)));
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<?> disableTwoFactor(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @RequestBody Map<String, String> body) {
        User u = resolveUser(authorization);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("detail", "unauthorized"));
        }
        if (!u.isTwoFactorEnabled()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "2FA is already disabled"));
        }

        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "code required"));
        }

        List<String> enabledChannels = getEnabledTwoFactorChannels(u);
        if (enabledChannels.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "No enabled 2FA method available"));
        }

        String requestedChannel = body.getOrDefault("channel", "");
        String channel;
        try {
            channel = resolveTwoFactorChannel(u, requestedChannel, enabledChannels, true);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(400).body(Map.of("detail", ex.getMessage(), "availableChannels", enabledChannels));
        }

        try {
            verifyChannelCodeOrThrow(u, channel, code);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(400).body(Map.of("detail", ex.getMessage()));
        }
        u.setTwoFactorEnabled(false);
        userRepository.save(u);
        return ResponseEntity.ok(Map.of("message", "2FA disabled", "user", userPayload(u)));
    }

    @PostMapping("/2fa/method/remove")
    public ResponseEntity<?> removeTwoFactorMethod(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                   @RequestBody Map<String, String> body) {
        User u = resolveUser(authorization);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("detail", "unauthorized"));
        }

        String removeChannel = body.getOrDefault("removeChannel", "").trim().toLowerCase();
        String code = body.get("code");
        if (removeChannel.isBlank() || !SUPPORTED_2FA_CHANNELS.contains(removeChannel)) {
            return ResponseEntity.badRequest().body(Map.of("detail", "removeChannel is invalid"));
        }
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "code required"));
        }

        List<String> configuredChannels = getConfiguredTwoFactorChannels(u);
        if (!configuredChannels.contains(removeChannel)) {
            return ResponseEntity.badRequest().body(Map.of("detail", "2FA method is not configured"));
        }

        List<String> enabledChannels = getEnabledTwoFactorChannels(u);
        if (enabledChannels.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "No enabled 2FA method available"));
        }

        String requestedVerifyChannel = body.getOrDefault("channel", "");
        String verifyChannel;
        try {
            verifyChannel = resolveTwoFactorChannel(u, requestedVerifyChannel, enabledChannels, true);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(400).body(Map.of("detail", ex.getMessage(), "availableChannels", enabledChannels));
        }

        try {
            verifyChannelCodeOrThrow(u, verifyChannel, code);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(400).body(Map.of("detail", ex.getMessage()));
        }

        configuredChannels.remove(removeChannel);
        if ("totp".equals(removeChannel)) {
            u.setTotpSecret(null);
        } else if ("passkey".equals(removeChannel)) {
            u.setPasskeyCodeHash(null);
        }

        if (configuredChannels.isEmpty()) {
            u.setTwoFactorEnabled(false);
            u.setTwoFactorChannels("email");
            u.setTwoFactorChannel("email");
        } else {
            u.setTwoFactorChannels(String.join(",", configuredChannels));
            if (!configuredChannels.contains((u.getTwoFactorChannel() == null ? "" : u.getTwoFactorChannel().toLowerCase()))) {
                u.setTwoFactorChannel(configuredChannels.get(0));
            }
            if (getEnabledTwoFactorChannels(u).isEmpty()) {
                u.setTwoFactorEnabled(false);
            }
        }

        userRepository.save(u);
        return ResponseEntity.ok(Map.of("message", "2FA method removed", "user", userPayload(u)));
    }

    @PostMapping("/2fa/setup/totp")
    public ResponseEntity<?> setupTotp(@RequestHeader(value = "Authorization", required = false) String authorization) {
        User u = resolveUser(authorization);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("detail", "unauthorized"));
        }
        String secret = u.getTotpSecret();
        if (secret == null || secret.isBlank()) {
            secret = generateTotpSecret();
            u.setTotpSecret(secret);
            userRepository.save(u);
        }
        String issuer = "TrainTicket";
        String label = issuer + ":" + u.getUsername();
        String otpAuthUrl = "otpauth://totp/" + urlEncode(label) + "?secret=" + secret + "&issuer=" + urlEncode(issuer) + "&digits=6&period=30";
        return ResponseEntity.ok(Map.of(
                "secret", secret,
                "otpauthUrl", otpAuthUrl,
                "message", "Scan this secret in Google Authenticator"
        ));
    }

    @PostMapping("/2fa/setup/passkey")
    public ResponseEntity<?> setupPasskeyCode(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @RequestBody Map<String, String> body) {
        User u = resolveUser(authorization);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("detail", "unauthorized"));
        }
        String passkeyCode = body.get("passkeyCode");
        if (passkeyCode == null || passkeyCode.length() < 4) {
            return ResponseEntity.badRequest().body(Map.of("detail", "passkeyCode must be at least 4 characters"));
        }
        u.setPasskeyCodeHash(passwordEncoder.encode(passkeyCode));
        userRepository.save(u);
        return ResponseEntity.ok(Map.of("message", "Passkey code configured"));
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("detail", "missing bearer token"));
        }
        String token = authorization.substring(7);
        try {
            Claims claims = jwtService.parseToken(token);
            String username = claims.getSubject();
            var userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("detail", "user not found"));
            }
            User u = userOpt.get();
            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "user", Map.of(
                            "id", u.getId(),
                            "username", u.getUsername(),
                            "role", u.getRole(),
                            "fullName", u.getFullName() == null ? "" : u.getFullName()
                    )
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(401).body(Map.of("detail", "invalid token"));
        }
    }

    @GetMapping("/key")
    public ResponseEntity<?> getPublicKey() {
        try {
            PublicKey pk = jwtService.getPublicKey();
            X509EncodedKeySpec spec = new X509EncodedKeySpec(pk.getEncoded());
            String encodedKey = Base64.getEncoder().encodeToString(pk.getEncoded());
            return ResponseEntity.ok(Map.of(
                "key", encodedKey,
                "algorithm", "RSA",
                "format", "X.509"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("detail", "failed to get public key"));
        }
    }

    private User resolveUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        try {
            Claims claims = jwtService.parseToken(authorization.substring(7));
            String username = claims.getSubject();
            return userRepository.findByUsername(username).orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> userPayload(User u) {
        List<String> configuredChannels = getConfiguredTwoFactorChannels(u);
        List<String> enabledChannels = getEnabledTwoFactorChannels(u);
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", u.getId());
        payload.put("username", u.getUsername());
        payload.put("role", u.getRole());
        payload.put("fullName", u.getFullName() == null ? "" : u.getFullName());
        payload.put("email", u.getEmail() == null ? "" : u.getEmail());
        payload.put("phone", u.getPhone() == null ? "" : u.getPhone());
        payload.put("twoFactorEnabled", u.isTwoFactorEnabled());
        payload.put("twoFactorChannel", u.getTwoFactorChannel());
        payload.put("twoFactorChannels", configuredChannels);
        payload.put("enabledTwoFactorChannels", enabledChannels);
        payload.put("supportedTwoFactorChannels", SUPPORTED_2FA_CHANNELS);
        payload.put("hasTotpSecret", u.getTotpSecret() != null && !u.getTotpSecret().isBlank());
        payload.put("hasPasskeyCode", u.getPasskeyCodeHash() != null && !u.getPasskeyCodeHash().isBlank());
        return payload;
    }

    private String resolveTwoFactorChannel(User user, String requestedChannel, List<String> allowedChannels, boolean strictRequested) {
        if (allowedChannels == null || allowedChannels.isEmpty()) {
            return "email";
        }

        if (requestedChannel != null && !requestedChannel.isBlank()) {
            String requested = requestedChannel.trim().toLowerCase();
            if (!SUPPORTED_2FA_CHANNELS.contains(requested)) {
                if (strictRequested) {
                    throw new IllegalArgumentException("2FA method is not supported");
                }
            } else if (allowedChannels.contains(requested)) {
                return requested;
            } else if (strictRequested) {
                throw new IllegalArgumentException("Selected 2FA method is not enabled or not configured yet");
            }
        }

        String preferred = requestedChannel == null || requestedChannel.isBlank()
                ? user.getTwoFactorChannel()
                : requestedChannel;
        String channel = preferred == null ? "email" : preferred.toLowerCase();
        if (allowedChannels.contains(channel)) {
            return channel;
        }

        String fallback = user.getTwoFactorChannel() == null ? "" : user.getTwoFactorChannel().toLowerCase();
        if (allowedChannels.contains(fallback)) {
            return fallback;
        }
        return allowedChannels.get(0);
    }

    private String resolveTwoFactorDestination(User user, String channel) {
        if ("sms".equalsIgnoreCase(channel)) {
            if (user.getPhone() == null || user.getPhone().isBlank()) {
                throw new IllegalStateException("Phone number is required for SMS 2FA");
            }
            return user.getPhone();
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalStateException("Email is required for email 2FA");
        }
        return user.getEmail();
    }

    private String generateTotpSecret() {
        byte[] buffer = new byte[20];
        random.nextBytes(buffer);
        return base32Encode(buffer);
    }

    private boolean verifyTotpCode(String secret, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            return false;
        }
        long timestep = Duration.ofSeconds(30).toMillis();
        long now = System.currentTimeMillis();
        for (int i = -1; i <= 1; i++) {
            long counter = (now / timestep) + i;
            String expected = generateTotpCode(secret, counter);
            if (code.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private String generateTotpCode(String secret, long counter) {
        try {
            byte[] key = base32Decode(secret);
            byte[] data = new byte[8];
            long value = counter;
            for (int i = 7; i >= 0; i--) {
                data[i] = (byte) (value & 0xFF);
                value >>= 8;
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format("%06d", otp);
        } catch (Exception ex) {
            return "000000";
        }
    }

    private String base32Encode(byte[] bytes) {
        final char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        StringBuilder result = new StringBuilder();
        int current = 0;
        int bits = 0;
        for (byte b : bytes) {
            current = (current << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                result.append(alphabet[(current >> (bits - 5)) & 0x1F]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            result.append(alphabet[(current << (5 - bits)) & 0x1F]);
        }
        return result.toString();
    }

    private byte[] base32Decode(String base32) {
        String normalized = base32.toUpperCase().replace("=", "").replace(" ", "");
        if (normalized.isEmpty()) {
            return new byte[0];
        }
        Map<Character, Integer> map = new HashMap<>();
        char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        for (int i = 0; i < alphabet.length; i++) {
            map.put(alphabet[i], i);
        }

        List<Byte> output = new ArrayList<>();
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : normalized.toCharArray()) {
            Integer val = map.get(c);
            if (val == null) {
                continue;
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output.add((byte) ((buffer >> (bitsLeft - 8)) & 0xFF));
                bitsLeft -= 8;
            }
        }
        byte[] decoded = new byte[output.size()];
        for (int i = 0; i < output.size(); i++) {
            decoded[i] = output.get(i);
        }
        return decoded;
    }

    private String urlEncode(String value) {
        return value.replace(" ", "%20").replace(":", "%3A");
    }

    private List<String> getConfiguredTwoFactorChannels(User user) {
        LinkedHashSet<String> channels = new LinkedHashSet<>();

        String raw = user.getTwoFactorChannels();
        if (raw != null && !raw.isBlank()) {
            String[] parts = raw.split(",");
            for (String part : parts) {
                String channel = part == null ? "" : part.trim().toLowerCase();
                if (SUPPORTED_2FA_CHANNELS.contains(channel)) {
                    channels.add(channel);
                }
            }
        }

        String preferred = user.getTwoFactorChannel();
        if (preferred != null && SUPPORTED_2FA_CHANNELS.contains(preferred.toLowerCase())) {
            channels.add(preferred.toLowerCase());
        }

        if (channels.isEmpty()) {
            channels.add("email");
        }

        return new ArrayList<>(channels);
    }

    private List<String> getEnabledTwoFactorChannels(User user) {
        List<String> configured = getConfiguredTwoFactorChannels(user);
        return configured.stream().filter(channel -> {
            if ("email".equals(channel)) {
                return user.getEmail() != null && !user.getEmail().isBlank();
            }
            if ("sms".equals(channel)) {
                return user.getPhone() != null && !user.getPhone().isBlank();
            }
            if ("totp".equals(channel)) {
                return user.getTotpSecret() != null && !user.getTotpSecret().isBlank();
            }
            if ("passkey".equals(channel)) {
                return user.getPasskeyCodeHash() != null && !user.getPasskeyCodeHash().isBlank();
            }
            return false;
        }).collect(Collectors.toList());
    }

    private void ensureChannelConfigured(User user, String channel) {
        List<String> configured = getConfiguredTwoFactorChannels(user);
        String normalized = channel == null ? "" : channel.toLowerCase();
        if (!SUPPORTED_2FA_CHANNELS.contains(normalized)) {
            return;
        }
        if (!configured.contains(normalized)) {
            configured.add(normalized);
        }
        user.setTwoFactorChannels(String.join(",", configured));
        user.setTwoFactorChannel(normalized);
    }

    private void pushDemoTwoFactorInbox(String username, String channel, String destination, String code) {
        if (!twoFactorDemoMode || username == null || username.isBlank()) {
            return;
        }

        String key = username.trim().toLowerCase();
        Deque<Map<String, Object>> queue = twoFactorInbox.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());

        Map<String, Object> message = new HashMap<>();
        message.put("channel", channel);
        message.put("destination", destination == null ? "" : destination);
        message.put("code", code);
        message.put("createdAt", Instant.now().toString());
        message.put("expiresAt", Instant.now().plusSeconds(300).toString());

        queue.addFirst(Collections.unmodifiableMap(message));
        while (queue.size() > 20) {
            queue.pollLast();
        }
    }

    private void verifyChannelCodeOrThrow(User user, String channel, String code) {
        if ("totp".equals(channel)) {
            if (user.getTotpSecret() == null || user.getTotpSecret().isBlank() || !verifyTotpCode(user.getTotpSecret(), code)) {
                throw new IllegalArgumentException("invalid authenticator code");
            }
            return;
        }

        if ("passkey".equals(channel)) {
            if (user.getPasskeyCodeHash() == null || user.getPasskeyCodeHash().isBlank() || !passwordEncoder.matches(code, user.getPasskeyCodeHash())) {
                throw new IllegalArgumentException("invalid passkey code");
            }
            return;
        }

        var challengeOpt = twoFactorChallengeRepository.findTopByUsernameAndChannelAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(
                user.getUsername(), channel, LocalDateTime.now());
        if (challengeOpt.isEmpty()) {
            throw new IllegalArgumentException("no active 2FA challenge");
        }
        TwoFactorChallenge challenge = challengeOpt.get();
        if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
            throw new IllegalArgumentException("invalid code");
        }
        challenge.setUsedAt(LocalDateTime.now());
        twoFactorChallengeRepository.save(challenge);
    }
}
