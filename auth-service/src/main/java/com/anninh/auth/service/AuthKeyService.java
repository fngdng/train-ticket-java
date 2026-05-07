package com.anninh.auth.service;

import com.anninh.auth.model.AuthKeyPair;
import com.anninh.auth.repo.AuthKeyPairRepository;
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
public class AuthKeyService {
    private final AuthKeyPairRepository repository;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    public AuthKeyService(AuthKeyPairRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void init() {
        try {
            AuthKeyPair keyPair = repository.findAll().stream().findFirst().orElse(null);
            if (keyPair == null) {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair generated = generator.generateKeyPair();
                keyPair = new AuthKeyPair();
                keyPair.setPrivateKey(Base64.getEncoder().encodeToString(generated.getPrivate().getEncoded()));
                keyPair.setPublicKey(Base64.getEncoder().encodeToString(generated.getPublic().getEncoded()));
                repository.save(keyPair);
            }
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyPair.getPrivateKey())));
            this.publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(keyPair.getPublicKey())));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize auth key pair", e);
        }
    }

    public PrivateKey getPrivateKey() { return privateKey; }
    public PublicKey getPublicKey() { return publicKey; }
}