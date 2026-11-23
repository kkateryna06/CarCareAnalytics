package com.carcare.vehicle;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    Page<Vehicle> findAllByOwnerId(Long ownerId, Pageable pageable);
    Optional<Vehicle> findByIdAndOwnerId(Long id, Long ownerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.id=:id and v.owner.id=:ownerId")
    Optional<Vehicle> lockOwned(Long id, Long ownerId);
}
