package com.anninh.bookings.repo;

import com.anninh.bookings.model.TicketKeyPair;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketKeyPairRepository extends JpaRepository<TicketKeyPair, Long> {
}
