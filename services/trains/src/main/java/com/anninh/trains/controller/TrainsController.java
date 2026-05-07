package com.anninh.trains.controller;

import com.anninh.trains.model.Train;
import com.anninh.trains.repo.TrainRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/trains")
public class TrainsController {
    private final TrainRepository repository;

    public TrainsController(TrainRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String date) {
        return repository.findAll().stream()
                .filter(Train::isActive)
                .filter(train -> from == null || from.isBlank() || train.getOrigin().equalsIgnoreCase(city(from)))
                .filter(train -> to == null || to.isBlank() || train.getDestination().equalsIgnoreCase(city(to)))
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    @GetMapping
    public List<Map<String, Object>> all() {
        return repository.findAll().stream().map(this::toMap).collect(Collectors.toList());
    }

    private Map<String, Object> toMap(Train train) {
        return Map.of(
                "name", train.getCode(),
                "code", train.getCode(),
                "from", train.getOrigin(),
                "to", train.getDestination(),
                "departure", train.getDeparture(),
                "arrival", train.getArrival(),
                "price", train.getPrice(),
                "availableSeats", train.getAvailableSeats(),
                "active", train.isActive()
        );
    }

    private static String city(String code) {
        return switch (code.toUpperCase()) {
            case "HN" -> "Hanoi";
            case "HCM" -> "Ho Chi Minh";
            case "DN" -> "Da Nang";
            case "HP" -> "Hai Phong";
            default -> code;
        };
    }
}
