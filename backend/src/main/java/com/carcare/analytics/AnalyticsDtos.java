package com.carcare.analytics;

import com.carcare.journal.JournalDtos.TimelineItem;
import com.carcare.journal.JournalTypes.Category;
import com.carcare.reminder.ReminderDtos.RemindersResponse;
import com.carcare.vehicle.VehicleResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}
    public record Period(LocalDate dateFrom,LocalDate dateTo,int monthCount) {}
    public record DistanceWindow(LocalDate firstDate,LocalDate lastDate,int firstOdometerKm,int lastOdometerKm) {}
    public record Summary(Period period,BigDecimal totalExpensesPln,BigDecimal monthAveragePln,Long distanceKm,
        BigDecimal costPerKmPln,long recordCount,DistanceWindow distanceWindow,List<String> warnings) {}
    public record CategoryTotal(Category category,BigDecimal amountPln,BigDecimal sharePercent) {}
    public record Categories(Period period,List<CategoryTotal> content) {}
    public record MonthTotal(String month,BigDecimal amountPln) {}
    public record Monthly(Period period,List<MonthTotal> content) {}
    public record Fuel(Period period,BigDecimal totalLiters,BigDecimal totalCostPln,BigDecimal averagePricePerLiterPln,
        BigDecimal averageLitersPer100Km,long measuredDistanceKm,BigDecimal measuredLiters,int cycleCount,int refuelingCount,List<String> warnings) {}
    public record Dashboard(VehicleResponse vehicle,Summary summary,Categories categories,Monthly monthly,Fuel fuel,
        BigDecimal lifetimeExpensesPln,BigDecimal currentMonthExpensesPln,RemindersResponse reminders,List<TimelineItem> recentOperations) {}
}
