package com.anninh.auth.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role = "USER";

    @Column(name = "full_name")
    private String fullName;

    private String email;

    private String phone;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled = false;

    @Column(name = "two_factor_channel", nullable = false)
    private String twoFactorChannel = "email";

    @Column(name = "two_factor_channels", nullable = false)
    private String twoFactorChannels = "email";

    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(name = "passkey_code_hash")
    private String passkeyCodeHash;

    public User() {}

    public User(String username, String password, String fullName) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isTwoFactorEnabled() { return twoFactorEnabled; }
    public void setTwoFactorEnabled(boolean twoFactorEnabled) { this.twoFactorEnabled = twoFactorEnabled; }
    public String getTwoFactorChannel() { return twoFactorChannel; }
    public void setTwoFactorChannel(String twoFactorChannel) { this.twoFactorChannel = twoFactorChannel; }
    public String getTwoFactorChannels() { return twoFactorChannels; }
    public void setTwoFactorChannels(String twoFactorChannels) { this.twoFactorChannels = twoFactorChannels; }
    public String getTotpSecret() { return totpSecret; }
    public void setTotpSecret(String totpSecret) { this.totpSecret = totpSecret; }
    public String getPasskeyCodeHash() { return passkeyCodeHash; }
    public void setPasskeyCodeHash(String passkeyCodeHash) { this.passkeyCodeHash = passkeyCodeHash; }
}
