package com.anninh.bookings.repo;

import com.anninh.bookings.model.MomoTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MomoTransactionRepository extends JpaRepository<MomoTransaction, UUID> {
    Optional<MomoTransaction> findByOrderId(String orderId);
    Optional<MomoTransaction> findByBookingId(Long bookingId);
}
