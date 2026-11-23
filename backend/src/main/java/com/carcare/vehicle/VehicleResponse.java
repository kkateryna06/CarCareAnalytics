package com.carcare.vehicle;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public record VehicleResponse(Long id, String make, String model, Integer year, String generation, FuelType fuelType,
        BigDecimal engineCapacityLiters, String vin, String registrationNumber, LocalDate purchaseDate,
        Integer currentOdometerKm, Instant createdAt, Instant updatedAt, UUID photoVersion) {}
