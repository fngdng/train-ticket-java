package com.anninh.auth.repo;

import com.anninh.auth.model.AuthKeyPair;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthKeyPairRepository extends JpaRepository<AuthKeyPair, Long> {
}