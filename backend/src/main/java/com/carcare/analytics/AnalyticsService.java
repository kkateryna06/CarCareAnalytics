package com.carcare.analytics;

import com.carcare.exception.ApiException;
import com.carcare.journal.JournalQueryService;
import com.carcare.journal.JournalTypes.Category;
import com.carcare.reminder.ReminderService;
import com.carcare.vehicle.VehicleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.carcare.analytics.AnalyticsDtos.*;
import com.carcare.analytics.AnalyticsDtos.Period;

@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class AnalyticsService {
    private final VehicleRepository vehicles;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ReminderService reminders;
    private final JournalQueryService journal;
    public AnalyticsService(VehicleRepository vehicles,JdbcTemplate jdbc,Clock clock,ReminderService reminders,JournalQueryService journal) {
        this.vehicles=vehicles; this.jdbc=jdbc; this.clock=clock; this.reminders=reminders; this.journal=journal;
    }
    public Period period(LocalDate from,LocalDate to,String timeZone) {
        var today=LocalDate.now(clock.withZone(ReminderService.zone(timeZone)));
        if(from==null) from=today.withDayOfMonth(1); if(to==null) to=today;
        if(from.isAfter(to)) throw ApiException.invalid(Map.of("dateTo","Конец периода не может быть раньше начала"));
        long months=ChronoUnit.MONTHS.between(YearMonth.from(from),YearMonth.from(to))+1;
        if(months>1200) throw ApiException.invalid(Map.of("dateFrom","Период не может превышать 1200 календарных месяцев"));
        return new Period(from,to,(int)months);
    }
    public Summary summary(long user,long vehicle,Period p) {
        own(user,vehicle);
        var total=sum(vehicle,p.dateFrom(),p.dateTo());
        long count=jdbc.queryForObject("SELECT count(*) FROM expenses WHERE vehicle_id=? AND date BETWEEN ? AND ?",Long.class,vehicle,p.dateFrom(),p.dateTo());
        var rows=jdbc.query("SELECT date,odometer_km FROM odometer_entries WHERE vehicle_id=? AND date BETWEEN ? AND ? ORDER BY date,odometer_km,id",
            (rs,row)->Map.entry(rs.getObject(1,LocalDate.class),rs.getInt(2)),vehicle,p.dateFrom(),p.dateTo());
        Long distance=null; DistanceWindow window=null; BigDecimal perKm=null; var warnings=new ArrayList<String>();
        if(rows.size()>=2) {
            var first=rows.getFirst(); var last=rows.getLast(); distance=(long)last.getValue()-first.getValue();
            window=new DistanceWindow(first.getKey(),last.getKey(),first.getValue(),last.getValue());
            if(distance>0) perKm=total.divide(BigDecimal.valueOf(distance),4,RoundingMode.HALF_UP); else warnings.add("ZERO_DISTANCE");
            if(!first.getKey().equals(p.dateFrom()) || !last.getKey().equals(p.dateTo())) warnings.add("PARTIAL_ODOMETER_COVERAGE");
        } else warnings.add("NO_ODOMETER_PAIR");
        return new Summary(p,total,total.divide(BigDecimal.valueOf(p.monthCount()),2,RoundingMode.HALF_UP),distance,perKm,count,window,warnings);
    }
    public Categories categories(long user,long vehicle,Period p) {
        own(user,vehicle); var amounts=new EnumMap<Category,BigDecimal>(Category.class);
        jdbc.query("SELECT category,sum(amount_pln) FROM expenses WHERE vehicle_id=? AND date BETWEEN ? AND ? GROUP BY category",rs->{ amounts.put(Category.valueOf(rs.getString(1)),rs.getBigDecimal(2)); },vehicle,p.dateFrom(),p.dateTo());
        var total=amounts.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        var content=Arrays.stream(Category.values()).map(c->{ var amount=amounts.getOrDefault(c,BigDecimal.ZERO).setScale(2); return new CategoryTotal(c,amount,total.signum()>0?amount.multiply(BigDecimal.valueOf(100)).divide(total,2,RoundingMode.HALF_UP):null); }).toList();
        return new Categories(p,content);
    }
    public Monthly monthly(long user,long vehicle,Period p) {
        own(user,vehicle); var amounts=new HashMap<YearMonth,BigDecimal>();
        jdbc.query("SELECT date_trunc('month',date)::date,sum(amount_pln) FROM expenses WHERE vehicle_id=? AND date BETWEEN ? AND ? GROUP BY 1",rs->{ amounts.put(YearMonth.from(rs.getObject(1,LocalDate.class)),rs.getBigDecimal(2)); },vehicle,p.dateFrom(),p.dateTo());
        var content=new ArrayList<MonthTotal>(); var start=YearMonth.from(p.dateFrom());
        for(int i=0;i<p.monthCount();i++) { var month=start.plusMonths(i); content.add(new MonthTotal(month.toString(),amounts.getOrDefault(month,BigDecimal.ZERO).setScale(2))); }
        return new Monthly(p,content);
    }
    public Fuel fuel(long user,long vehicle,Period p) {
        own(user,vehicle);
        var rows=jdbc.query("SELECT r.odometer_km,r.liters,e.amount_pln,r.is_full_tank FROM refuelings r JOIN expenses e ON e.id=r.expense_id WHERE r.vehicle_id=? AND r.date BETWEEN ? AND ? ORDER BY r.date,r.odometer_km,r.id",
            (rs,row)->new FuelCalculator.Fill(rs.getInt(1),rs.getBigDecimal(2),rs.getBigDecimal(3),rs.getBoolean(4)),vehicle,p.dateFrom(),p.dateTo());
        var result=FuelCalculator.calculate(rows);
        return new Fuel(p,result.totalLiters(),result.totalCostPln(),result.averagePricePerLiterPln(),result.averageLitersPer100Km(),result.measuredDistanceKm(),result.measuredLiters(),result.cycleCount(),rows.size(),result.warnings());
    }
    public Dashboard dashboard(long user,long vehicle,Period p,String timeZone) {
        var car=vehicles.findByIdAndOwnerId(vehicle,user).orElseThrow(ApiException::notFound);
        var today=LocalDate.now(clock.withZone(ReminderService.zone(timeZone)));
        var lifetime=jdbc.queryForObject("SELECT coalesce(sum(amount_pln),0) FROM expenses WHERE vehicle_id=?",BigDecimal.class,vehicle).setScale(2);
        return new Dashboard(car.response(),summary(user,vehicle,p),categories(user,vehicle,p),monthly(user,vehicle,p),fuel(user,vehicle,p),lifetime,
            sum(vehicle,today.withDayOfMonth(1),today),reminders.list(user,vehicle,timeZone,false),journal.timeline(user,vehicle,0,5,"date,desc",null,null,null,null).content());
    }
    private BigDecimal sum(long vehicle,LocalDate from,LocalDate to) { return jdbc.queryForObject("SELECT coalesce(sum(amount_pln),0) FROM expenses WHERE vehicle_id=? AND date BETWEEN ? AND ?",BigDecimal.class,vehicle,from,to).setScale(2); }
    private void own(long user,long vehicle) { vehicles.findByIdAndOwnerId(vehicle,user).orElseThrow(ApiException::notFound); }
}
