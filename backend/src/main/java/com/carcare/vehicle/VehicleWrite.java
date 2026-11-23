package com.carcare.vehicle;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

public record VehicleWrite(
        @NotBlank(message = "Укажите марку") @Size(max = 80) String make,
        @NotBlank(message = "Укажите модель") @Size(max = 80) String model,
        @NotNull @Min(value = 1886, message = "Год должен быть не раньше 1886") @Max(2100) Integer year,
        @Size(max = 80) String generation,
        @NotNull(message = "Выберите тип топлива") FuelType fuelType,
        @DecimalMin(value = "0.00", inclusive = false) @DecimalMax("20.00") @Digits(integer = 2, fraction = 2) BigDecimal engineCapacityLiters,
        @Pattern(regexp = "[A-HJ-NPR-Z0-9]{17}", message = "VIN: 17 символов, без I, O и Q") String vin,
        @Size(max = 20) String registrationNumber,
        @PastOrPresent(message = "Дата покупки не может быть в будущем") LocalDate purchaseDate,
        @NotNull @Min(value = 0, message = "Пробег не может быть отрицательным") Integer currentOdometerKm,
        @Size(max = VehiclePhoto.MAX_BASE64) String photoBase64) {
    public VehicleWrite(String make, String model, Integer year, String generation, FuelType fuelType, BigDecimal engineCapacityLiters,
                        String vin, String registrationNumber, LocalDate purchaseDate, Integer currentOdometerKm) {
        this(make, model, year, generation, fuelType, engineCapacityLiters, vin, registrationNumber, purchaseDate, currentOdometerKm, null);
    }
    public VehicleWrite {
        make = clean(make); model = clean(model); generation = clean(generation);
        vin = upper(vin); registrationNumber = upper(registrationNumber);
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static String upper(String value) { String result = clean(value); return result == null ? null : result.toUpperCase(Locale.ROOT); }
}
