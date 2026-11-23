package com.carcare.journal;

import jakarta.persistence.*;
import java.time.Instant;

@MappedSuperclass
public abstract class JournalEntry {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(name="vehicle_id", nullable=false) Long vehicleId;
    @Column(name="created_at", nullable=false, updatable=false) Instant createdAt;
    @Column(name="updated_at", nullable=false) Instant updatedAt;
}
