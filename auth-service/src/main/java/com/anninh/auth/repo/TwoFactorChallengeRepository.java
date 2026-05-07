package com.anninh.auth.repo;

import com.anninh.auth.model.TwoFactorChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TwoFactorChallengeRepository extends JpaRepository<TwoFactorChallenge, Long> {
    Optional<TwoFactorChallenge> findTopByUsernameAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(String username, LocalDateTime now);
    Optional<TwoFactorChallenge> findTopByUsernameAndChannelAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(String username, String channel, LocalDateTime now);
}