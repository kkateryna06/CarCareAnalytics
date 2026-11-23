package com.carcare.journal;

public interface OdometerEntryRepository extends JournalRepository<OdometerEntry> {
    @org.springframework.data.jpa.repository.Query("select max(e.odometerKm) from OdometerEntry e where e.vehicleId=:vehicleId and e.date < :date and (:excluded is null or e.id <> :excluded)")
    Integer previousMaximum(long vehicleId, java.time.LocalDate date, Long excluded);
    @org.springframework.data.jpa.repository.Query("select min(e.odometerKm) from OdometerEntry e where e.vehicleId=:vehicleId and e.date > :date and (:excluded is null or e.id <> :excluded)")
    Integer nextMinimum(long vehicleId, java.time.LocalDate date, Long excluded);
    @org.springframework.data.jpa.repository.Query("select coalesce(max(e.odometerKm),0) from OdometerEntry e where e.vehicleId=:vehicleId")
    Integer maximum(long vehicleId);
}
