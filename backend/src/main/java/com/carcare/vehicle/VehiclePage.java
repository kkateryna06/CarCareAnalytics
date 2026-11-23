package com.carcare.vehicle;

import org.springframework.data.domain.Page;
import java.util.List;

public record VehiclePage(List<VehicleResponse> content, int page, int size, long totalElements, int totalPages) {
    public static VehiclePage from(Page<Vehicle> page) {
        return new VehiclePage(page.getContent().stream().map(Vehicle::response).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
