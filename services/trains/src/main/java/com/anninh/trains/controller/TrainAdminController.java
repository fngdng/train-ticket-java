package com.anninh.trains.controller;

import com.anninh.trains.model.Train;
import com.anninh.trains.repo.TrainRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/trains/admin")
public class TrainAdminController {
    private final TrainRepository repository;

    public TrainAdminController(TrainRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Train train) {
        if (train.getCode() == null || train.getCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "code required"));
        }
        if (repository.existsByCode(train.getCode())) {
            return ResponseEntity.status(409).body(Map.of("detail", "train code exists"));
        }
        return ResponseEntity.ok(repository.save(train));
    }

    @PutMapping("/{code}")
    public ResponseEntity<?> update(@PathVariable String code, @RequestBody Train payload) {
        Train train = repository.findByCode(code).orElse(null);
        if (train == null) {
            return ResponseEntity.status(404).body(Map.of("detail", "train not found"));
        }
        train.setOrigin(payload.getOrigin());
        train.setDestination(payload.getDestination());
        train.setDeparture(payload.getDeparture());
        train.setArrival(payload.getArrival());
        train.setPrice(payload.getPrice());
        train.setAvailableSeats(payload.getAvailableSeats());
        train.setActive(payload.isActive());
        return ResponseEntity.ok(repository.save(train));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<?> delete(@PathVariable String code) {
        Train train = repository.findByCode(code).orElse(null);
        if (train == null) {
            return ResponseEntity.status(404).body(Map.of("detail", "train not found"));
        }
        repository.delete(train);
        return ResponseEntity.ok(Map.of("message", "deleted"));
    }
}