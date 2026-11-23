package com.carcare.journal;

import com.carcare.exception.ApiException;
import com.carcare.vehicle.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static com.carcare.journal.JournalTypes.*;
import static com.carcare.journal.JournalDtos.*;

@Service @Transactional
public class JournalService {
    private final VehicleRepository vehicles;
    private final OdometerEntryRepository odometers;
    private final RefuelingRepository refuelings;
    private final ExpenseRepository expenses;
    private final ServiceRecordRepository services;
    private final DocumentDeadlineRepository documents;
    private final MileageService mileage;
    private final JournalRequests requests;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public JournalService(VehicleRepository vehicles, OdometerEntryRepository odometers, RefuelingRepository refuelings,
        ExpenseRepository expenses, ServiceRecordRepository services, DocumentDeadlineRepository documents,
        MileageService mileage, JournalRequests requests, JdbcTemplate jdbc, Clock clock) {
        this.vehicles=vehicles; this.odometers=odometers; this.refuelings=refuelings; this.expenses=expenses; this.services=services;
        this.documents=documents; this.mileage=mileage; this.requests=requests; this.jdbc=jdbc; this.clock=clock;
    }
    public Object create(long userId, long vehicleId, Kind kind, JsonNode body, String key) {
        var vehicle=vehicles.lockOwned(vehicleId,userId).orElseThrow(ApiException::notFound);
        Object data=requests.decode(kind,body,null);
        String hash=hash(requests.tree(data).toString());
        if (key!=null) {
            if (!key.matches("[A-Za-z0-9._:-]{1,128}")) throw new ApiException(HttpStatus.BAD_REQUEST,"BAD_IDEMPOTENCY_KEY","Некорректный Idempotency-Key");
            var prior=jdbc.queryForList("SELECT request_hash,result_id FROM journal_commands WHERE vehicle_id=? AND resource=? AND request_key=?",vehicleId,kind.resource,key);
            if (!prior.isEmpty()) {
                if (!hash.equals(prior.getFirst().get("request_hash"))) throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","Этот ключ уже использован для другого запроса");
                long id=((Number)prior.getFirst().get("result_id")).longValue();
                var found=repository(kind).findById(id);
                if (found.isEmpty()) throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_RESULT_DELETED","Созданная запись уже удалена");
                return response(found.get());
            }
        }
        JournalEntry entry=write(vehicle,kind,null,data);
        if (key!=null) jdbc.update("INSERT INTO journal_commands(vehicle_id,resource,request_key,request_hash,result_id) VALUES (?,?,?,?,?)",vehicleId,kind.resource,key,hash,entry.id);
        return response(entry);
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Object get(long userId, Kind kind, long id) { return response(owned(userId,kind,id,false)); }
    public Object patch(long userId, Kind kind, long id, JsonNode body) {
        var entry=owned(userId,kind,id,true);
        editable(entry);
        Object data=requests.decode(kind,body,writeData(entry));
        var vehicle=vehicles.findById(entry.vehicleId).orElseThrow(ApiException::notFound);
        return response(write(vehicle,kind,entry,data));
    }
    public void delete(long userId, Kind kind, long id) {
        var entry=owned(userId,kind,id,true); editable(entry);
        var vehicle=vehicles.findById(entry.vehicleId).orElseThrow(ApiException::notFound);
        if (entry instanceof Refueling r) {
            refuelings.delete(r); refuelings.flush(); expenses.deleteById(r.expenseId); mileage.remove(vehicle,r.odometerEntryId);
        } else if (entry instanceof ServiceRecord s) {
            services.delete(s); services.flush(); if(s.expenseId!=null) expenses.deleteById(s.expenseId); mileage.remove(vehicle,s.odometerEntryId);
        } else if (entry instanceof OdometerEntry o) mileage.remove(vehicle,o.id);
        else if(entry instanceof Expense e) expenses.delete(e);
        else if(entry instanceof DocumentDeadline d) documents.delete(d);
        vehicle.refreshOdometer(vehicle.getCurrentOdometerKm(),clock.instant());
    }
    private JournalEntry owned(long userId, Kind kind, long id, boolean lock) {
        var repo=repository(kind);
        long vehicleId=repo.vehicleIdFor(id).orElseThrow(ApiException::notFound);
        if(lock) vehicles.lockOwned(vehicleId,userId).orElseThrow(ApiException::notFound);
        else vehicles.findByIdAndOwnerId(vehicleId,userId).orElseThrow(ApiException::notFound);
        return repo.findById(id).orElseThrow(ApiException::notFound);
    }
    private void editable(JournalEntry entry) {
        if ((entry instanceof OdometerEntry o && o.sourceType!=SourceType.MANUAL) || (entry instanceof Expense e && e.sourceType!=SourceType.MANUAL)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"SOURCE_MANAGED","Изменяйте или удаляйте исходную заправку/сервисную запись");
        }
    }
    private <T extends JournalEntry> T stamp(T entry, Vehicle vehicle) {
        if(entry.id==null) { entry.vehicleId=vehicle.getId(); entry.createdAt=clock.instant(); }
        entry.updatedAt=clock.instant(); vehicle.refreshOdometer(vehicle.getCurrentOdometerKm(),clock.instant()); return entry;
    }
    private JournalEntry write(Vehicle vehicle, Kind kind, JournalEntry existing, Object data) {
        return switch(kind) {
            case ODOMETER -> {
                var d=(OdometerWrite)data;
                Long id=mileage.upsert(vehicle,existing==null?null:existing.id,d.date(),d.odometerKm(),d.note(),SourceType.MANUAL);
                yield odometers.findById(id).orElseThrow();
            }
            case EXPENSE -> {
                var d=(ExpenseWrite)data; var e=existing==null?new Expense():(Expense)existing; stamp(e,vehicle);
                e.date=d.date(); e.category=d.category(); e.amountPln=d.amountPln(); e.title=d.title(); e.description=d.description(); e.sourceType=SourceType.MANUAL;
                yield expenses.saveAndFlush(e);
            }
            case REFUELING -> {
                var d=(RefuelingWrite)data; var r=existing==null?new Refueling():(Refueling)existing; stamp(r,vehicle);
                r.odometerEntryId=mileage.upsert(vehicle,r.odometerEntryId,d.date(),d.odometerKm(),d.note(),SourceType.FUEL);
                var e=linkedExpense(vehicle,r.expenseId,d.date(),Category.FUEL,d.totalCostPln(),"Заправка",d.note(),SourceType.FUEL);
                r.expenseId=e.id; r.date=d.date(); r.odometerKm=d.odometerKm(); r.liters=d.liters(); r.isFullTank=d.isFullTank(); r.station=d.station(); r.note=d.note();
                refuelings.saveAndFlush(r); e.sourceId=r.id; expenses.saveAndFlush(e); yield r;
            }
            case SERVICE -> {
                var d=(ServiceWrite)data; var s=existing==null?new ServiceRecord():(ServiceRecord)existing; stamp(s,vehicle);
                s.odometerEntryId=mileage.upsert(vehicle,s.odometerEntryId,d.date(),d.odometerKm(),d.description().substring(0,Math.min(1000,d.description().length())),SourceType.SERVICE);
                Long oldExpense=s.expenseId;
                if(d.costPln()!=null && d.costPln().signum()>0) {
                    var e=linkedExpense(vehicle,s.expenseId,d.date(),Category.SERVICE,d.costPln(),"Обслуживание: " + d.serviceType().name(),d.description(),SourceType.SERVICE);
                    s.expenseId=e.id;
                } else s.expenseId=null;
                if(!Objects.equals(s.nextDueDate,d.nextDueDate()) || !Objects.equals(s.nextDueOdometerKm,d.nextDueOdometerKm()) || !Objects.equals(s.serviceType,d.serviceType())) s.reminderDone=false;
                s.date=d.date(); s.serviceType=d.serviceType(); s.odometerKm=d.odometerKm(); s.description=d.description(); s.workshop=d.workshop(); s.nextDueDate=d.nextDueDate(); s.nextDueOdometerKm=d.nextDueOdometerKm();
                services.saveAndFlush(s);
                if(s.expenseId!=null) { var e=expenses.findById(s.expenseId).orElseThrow(); e.sourceId=s.id; expenses.saveAndFlush(e); }
                else if(oldExpense!=null) expenses.deleteById(oldExpense);
                yield s;
            }
            case DOCUMENT -> {
                var d=(DocumentWrite)data; var e=existing==null?new DocumentDeadline():(DocumentDeadline)existing; stamp(e,vehicle);
                e.type=d.type(); e.validUntil=d.validUntil(); e.note=d.note(); yield documents.saveAndFlush(e);
            }
        };
    }
    private Expense linkedExpense(Vehicle vehicle, Long id, LocalDate date, Category category, BigDecimal amount, String title, String description, SourceType source) {
        var e=id==null?new Expense():expenses.findById(id).orElseThrow(); stamp(e,vehicle);
        e.date=date; e.category=category; e.amountPln=amount; e.title=title; e.description=description; e.sourceType=source;
        return expenses.saveAndFlush(e);
    }
    Object writeData(JournalEntry entry) {
        if(entry instanceof OdometerEntry e) return new OdometerWrite(e.date,e.odometerKm,e.note);
        if(entry instanceof Refueling e) return new RefuelingWrite(e.date,e.odometerKm,e.liters,expenses.findById(e.expenseId).orElseThrow().amountPln,e.isFullTank,e.station,e.note);
        if(entry instanceof Expense e) return new ExpenseWrite(e.date,e.category,e.amountPln,e.title,e.description);
        if(entry instanceof ServiceRecord e) return new ServiceWrite(e.date,e.serviceType,e.odometerKm,e.description,e.workshop,e.expenseId==null?null:expenses.findById(e.expenseId).orElseThrow().amountPln,e.nextDueDate,e.nextDueOdometerKm);
        var e=(DocumentDeadline)entry; return new DocumentWrite(e.type,e.validUntil,e.note);
    }
    Object response(JournalEntry entry) {
        if(entry instanceof OdometerEntry e) return new OdometerResponse(e.id,e.vehicleId,e.date,e.odometerKm,e.note,e.sourceType,e.createdAt,e.updatedAt);
        if(entry instanceof Expense e) return new ExpenseResponse(e.id,e.vehicleId,e.date,e.category,e.amountPln,e.title,e.description,e.sourceType,e.sourceId,e.createdAt,e.updatedAt);
        if(entry instanceof Refueling e) {
            var cost=expenses.findById(e.expenseId).orElseThrow().amountPln;
            return new RefuelingResponse(e.id,e.vehicleId,e.date,e.odometerKm,e.liters,cost,cost.divide(e.liters,2,RoundingMode.HALF_UP),e.isFullTank,e.station,e.note,e.expenseId,e.createdAt,e.updatedAt);
        }
        if(entry instanceof ServiceRecord e) return new ServiceResponse(e.id,e.vehicleId,e.date,e.serviceType,e.odometerKm,e.description,e.workshop,e.expenseId==null?null:expenses.findById(e.expenseId).orElseThrow().amountPln,e.nextDueDate,e.nextDueOdometerKm,e.expenseId,e.createdAt,e.updatedAt);
        var e=(DocumentDeadline)entry; var today=LocalDate.now(clock);
        Long active=jdbc.query("SELECT id FROM document_deadlines WHERE vehicle_id=? AND type=? AND valid_until>=? ORDER BY valid_until DESC,id DESC LIMIT 1", rs -> rs.next()?rs.getLong(1):null,e.vehicleId,e.type.name(),today);
        String status=e.validUntil.isBefore(today)?"OVERDUE":e.validUntil.equals(today)?"DUE":!e.validUntil.isAfter(today.plusDays(30))?"UPCOMING":"VALID";
        return new DocumentResponse(e.id,e.vehicleId,e.type,e.validUntil,e.note,Objects.equals(e.id,active),status,e.createdAt,e.updatedAt);
    }
    JournalRepository<? extends JournalEntry> repository(Kind kind) {
        return switch(kind) { case ODOMETER -> odometers; case REFUELING -> refuelings; case EXPENSE -> expenses; case SERVICE -> services; case DOCUMENT -> documents; };
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
