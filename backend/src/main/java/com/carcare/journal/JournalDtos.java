package com.carcare.journal;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static com.carcare.journal.JournalTypes.*;

public final class JournalDtos {
    private JournalDtos() {}
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    public record OdometerWrite(@NotNull @PastOrPresent LocalDate date, @NotNull @Min(0) Integer odometerKm, @Size(max=1000) String note) {
        public OdometerWrite { note = clean(note); }
    }
    public record RefuelingWrite(@NotNull @PastOrPresent LocalDate date, @NotNull @Min(0) Integer odometerKm,
        @NotNull @DecimalMin(value="0", inclusive=false) @Digits(integer=7,fraction=3) BigDecimal liters,
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal totalCostPln,
        @NotNull Boolean isFullTank, @Size(max=120) String station, @Size(max=1000) String note) {
        public RefuelingWrite { station=clean(station); note=clean(note); }
    }
    public record ExpenseWrite(@NotNull @PastOrPresent LocalDate date, @NotNull Category category,
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amountPln,
        @NotBlank @Size(max=200) String title, @Size(max=2000) String description) {
        public ExpenseWrite { title=clean(title); description=clean(description); }
    }
    public record ServiceWrite(@NotNull @PastOrPresent LocalDate date, @NotNull ServiceType serviceType,
        @Min(0) Integer odometerKm, @NotBlank @Size(max=2000) String description, @Size(max=120) String workshop,
        @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal costPln, LocalDate nextDueDate, @Min(0) Integer nextDueOdometerKm) {
        public ServiceWrite { description=clean(description); workshop=clean(workshop); }
    }
    public record DocumentWrite(@NotNull DocumentType type, @NotNull LocalDate validUntil, @Size(max=1000) String note) {
        public DocumentWrite { note=clean(note); }
    }
    public record OdometerResponse(long id, long vehicleId, LocalDate date, int odometerKm, String note, SourceType sourceType, Instant createdAt, Instant updatedAt) {}
    public record RefuelingResponse(long id, long vehicleId, LocalDate date, int odometerKm, BigDecimal liters, BigDecimal totalCostPln,
        BigDecimal pricePerLiterPln, boolean isFullTank, String station, String note, long expenseId, Instant createdAt, Instant updatedAt) {}
    public record ExpenseResponse(long id, long vehicleId, LocalDate date, Category category, BigDecimal amountPln, String title, String description,
        SourceType sourceType, Long sourceId, Instant createdAt, Instant updatedAt) {}
    public record ServiceResponse(long id, long vehicleId, LocalDate date, ServiceType serviceType, Integer odometerKm, String description,
        String workshop, BigDecimal costPln, LocalDate nextDueDate, Integer nextDueOdometerKm, Long expenseId, Instant createdAt, Instant updatedAt) {}
    public record DocumentResponse(long id, long vehicleId, DocumentType type, LocalDate validUntil, String note, boolean active, String status, Instant createdAt, Instant updatedAt) {}
    public record TimelineItem(long id, Kind kind, LocalDate date, String title, BigDecimal amountPln, Integer odometerKm, Category category, Instant createdAt) {}
    public record JournalPage<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
}
