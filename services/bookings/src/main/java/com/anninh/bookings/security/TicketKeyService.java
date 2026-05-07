package com.anninh.bookings.security;

import com.anninh.bookings.model.TicketKeyPair;
import com.anninh.bookings.repo.TicketKeyPairRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Service
public class TicketKeyService {
    private final TicketKeyPairRepository repository;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    public TicketKeyService(TicketKeyPairRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void init() {
        try {
            TicketKeyPair keyPair = repository.findAll().stream().findFirst().orElse(null);
            if (keyPair == null) {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair generated = generator.generateKeyPair();
                keyPair = new TicketKeyPair();
                keyPair.setPrivateKey(Base64.getEncoder().encodeToString(generated.getPrivate().getEncoded()));
                keyPair.setPublicKey(Base64.getEncoder().encodeToString(generated.getPublic().getEncoded()));
                repository.save(keyPair);
            }
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyPair.getPrivateKey())));
            this.publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(keyPair.getPublicKey())));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize ticket key pair", e);
        }
    }

    public PrivateKey getPrivateKey() { return privateKey; }
    public PublicKey getPublicKey() { return publicKey; }
}
