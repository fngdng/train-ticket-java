package com.anninh.bookings.repo;

import com.anninh.bookings.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Booking findByBookingId(String bookingId);
    List<Booking> findByUserId(String userId);
}
