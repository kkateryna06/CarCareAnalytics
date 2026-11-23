package com.carcare.reminder;

import com.carcare.exception.ApiException;
import com.carcare.vehicle.VehicleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static com.carcare.reminder.ReminderDtos.*;

@Service
public class ReminderService {
    private final VehicleRepository vehicles;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public ReminderService(VehicleRepository vehicles,JdbcTemplate jdbc,Clock clock) { this.vehicles=vehicles; this.jdbc=jdbc; this.clock=clock; }
    public static ZoneId zone(String value) {
        try { return ZoneId.of(value); }
        catch(Exception ex) { throw ApiException.invalid(Map.of("timeZone","Укажите корректный часовой пояс IANA")); }
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public RemindersResponse list(long user,long vehicle,String timeZone,boolean includeDone) {
        var car=vehicles.findByIdAndOwnerId(vehicle,user).orElseThrow(ApiException::notFound);
        var today=LocalDate.now(clock.withZone(zone(timeZone))); int km=car.getCurrentOdometerKm();
        var result=new ArrayList<Reminder>();
        jdbc.query("SELECT id,type,valid_until,row_number() OVER (PARTITION BY type ORDER BY valid_until DESC,id DESC) AS rank FROM document_deadlines WHERE vehicle_id=?",
            rs->{ boolean done=rs.getInt("rank")>1; if(includeDone||!done) result.add(ReminderRules.evaluate("DOCUMENT",rs.getLong("id"),rs.getString("type"),rs.getString("type"),rs.getObject("valid_until",LocalDate.class),null,done,today,km)); },vehicle);
        jdbc.query("SELECT id,service_type,description,next_due_date,next_due_odometer_km,reminder_done FROM service_records WHERE vehicle_id=? AND (next_due_date IS NOT NULL OR next_due_odometer_km IS NOT NULL)",
            rs->{ boolean done=rs.getBoolean("reminder_done"); if(includeDone||!done) result.add(ReminderRules.evaluate("SERVICE",rs.getLong("id"),rs.getString("service_type"),rs.getString("description"),rs.getObject("next_due_date",LocalDate.class),rs.getObject("next_due_odometer_km",Integer.class),done,today,km)); },vehicle);
        result.sort(Comparator.comparing(Reminder::status).thenComparing(Reminder::dueDate,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Reminder::remainingKm,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Reminder::key));
        return new RemindersResponse(today,timeZone,km,result);
    }
    @Transactional
    public void complete(long user,long service,boolean done) {
        var ids=jdbc.queryForList("SELECT vehicle_id FROM service_records WHERE id=?",Long.class,service);
        if(ids.isEmpty()) throw ApiException.notFound();
        var car=vehicles.lockOwned(ids.getFirst(),user).orElseThrow(ApiException::notFound);
        int changed=jdbc.update("UPDATE service_records SET reminder_done=?,updated_at=? WHERE id=? AND vehicle_id=?",done,clock.instant().atOffset(ZoneOffset.UTC),service,car.getId());
        if(changed==0) throw ApiException.notFound();
        car.refreshOdometer(car.getCurrentOdometerKm(),clock.instant());
    }
}
