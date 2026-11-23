package com.carcare.journal;

import com.carcare.exception.ApiException;
import com.carcare.vehicle.Vehicle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.Map;
import static com.carcare.journal.JournalTypes.*;

@Service @Transactional
public class MileageService {
    private final OdometerEntryRepository entries;
    private final Clock clock;
    public MileageService(OdometerEntryRepository entries, Clock clock) { this.entries=entries; this.clock=clock; }
    public void initialize(Vehicle vehicle, int kilometers) { upsert(vehicle, null, LocalDate.now(clock), kilometers, "Начальный пробег", SourceType.MANUAL); }
    // All callers hold the same vehicle row lock, serializing concurrent measurements.
    public Long upsert(Vehicle vehicle, Long existing, LocalDate date, Integer kilometers, String note, SourceType source) {
        if (kilometers == null) { remove(vehicle, existing); return null; }
        Integer before=entries.previousMaximum(vehicle.getId(), date, existing);
        Integer after=entries.nextMinimum(vehicle.getId(), date, existing);
        if ((before!=null && kilometers<before) || (after!=null && kilometers>after)) {
            throw ApiException.invalid(Map.of("odometerKm", "Пробег не согласуется с соседними датами: минимум " + (before==null ? 0 : before) + (after==null ? "" : ", максимум " + after)));
        }
        var entry=existing==null ? new OdometerEntry() : entries.findById(existing).orElseThrow(ApiException::notFound);
        if (entry.id==null) { entry.vehicleId=vehicle.getId(); entry.createdAt=clock.instant(); }
        entry.date=date; entry.odometerKm=kilometers; entry.note=note; entry.sourceType=source; entry.updatedAt=clock.instant();
        entries.saveAndFlush(entry);
        recalculate(vehicle);
        return entry.id;
    }
    public void remove(Vehicle vehicle, Long id) { if (id!=null) { entries.deleteById(id); entries.flush(); } recalculate(vehicle); }
    private void recalculate(Vehicle vehicle) { vehicle.refreshOdometer(entries.maximum(vehicle.getId()), clock.instant()); }
}
