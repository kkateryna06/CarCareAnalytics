package com.carcare.vehicle;

import com.carcare.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity @Table(name = "vehicles")
public class Vehicle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User owner;
    @Column(nullable = false, length = 80) private String make;
    @Column(nullable = false, length = 80) private String model;
    @Column(nullable = false) private Integer year;
    @Column(length = 80) private String generation;
    @Enumerated(EnumType.STRING) @Column(name = "fuel_type", nullable = false, length = 20) private FuelType fuelType;
    @Column(name = "engine_capacity_liters", precision = 4, scale = 2) private BigDecimal engineCapacityLiters;
    @Column(length = 17) private String vin;
    @Column(name = "registration_number", length = 20) private String registrationNumber;
    @Column(name = "purchase_date") private LocalDate purchaseDate;
    @Column(name = "current_odometer_km", nullable = false) private Integer currentOdometerKm;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "photo_version") private UUID photoVersion;
    protected Vehicle() {}
    public Vehicle(User owner, VehicleWrite data, Instant now) { this.owner = owner; this.createdAt = now; update(data, now); }
    public void update(VehicleWrite data, Instant now) {
        make = data.make(); model = data.model(); year = data.year(); generation = data.generation(); fuelType = data.fuelType();
        engineCapacityLiters = data.engineCapacityLiters(); vin = data.vin(); registrationNumber = data.registrationNumber();
        purchaseDate = data.purchaseDate(); currentOdometerKm = data.currentOdometerKm(); updatedAt = now;
    }
    public Long getId() { return id; }
    public int getCurrentOdometerKm() { return currentOdometerKm; }
    public UUID getPhotoVersion() { return photoVersion; }
    public void setPhotoVersion(UUID version) { photoVersion = version; }
    public void refreshOdometer(int kilometers, Instant now) { currentOdometerKm = kilometers; updatedAt = now; }
    public VehicleWrite data() { return new VehicleWrite(make, model, year, generation, fuelType, engineCapacityLiters, vin, registrationNumber, purchaseDate, currentOdometerKm); }
    public VehicleResponse response() { return new VehicleResponse(id, make, model, year, generation, fuelType, engineCapacityLiters, vin, registrationNumber, purchaseDate, currentOdometerKm, createdAt, updatedAt, photoVersion); }
}
