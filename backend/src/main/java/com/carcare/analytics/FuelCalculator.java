package com.carcare.analytics;

import java.math.*;
import java.util.*;

public final class FuelCalculator {
    private FuelCalculator() {}
    public record Fill(int odometerKm,BigDecimal liters,BigDecimal costPln,boolean fullTank) {}
    public record Result(BigDecimal totalLiters,BigDecimal totalCostPln,BigDecimal averagePricePerLiterPln,
        BigDecimal averageLitersPer100Km,long measuredDistanceKm,BigDecimal measuredLiters,int cycleCount,List<String> warnings) {}
    // Fills must be ordered by operation date, mileage and ID. Both full-tank anchors are in the selected period.
    public static Result calculate(List<Fill> fills) {
        BigDecimal allLiters=BigDecimal.ZERO,allCost=BigDecimal.ZERO,cycleLiters=BigDecimal.ZERO,measuredLiters=BigDecimal.ZERO;
        long measuredKm=0; int cycles=0; Fill anchor=null; boolean trailing=false;
        Set<String> warnings=new LinkedHashSet<>();
        for(var fill:fills) {
            allLiters=allLiters.add(fill.liters()); allCost=allCost.add(fill.costPln());
            if(anchor==null) { if(fill.fullTank()) anchor=fill; else warnings.add("LEADING_PARTIAL_FILLS_IGNORED"); continue; }
            cycleLiters=cycleLiters.add(fill.liters()); trailing=true;
            if(fill.fullTank()) {
                long distance=(long)fill.odometerKm()-anchor.odometerKm();
                if(distance>0) { measuredKm+=distance; measuredLiters=measuredLiters.add(cycleLiters); cycles++; }
                else warnings.add("NON_POSITIVE_FUEL_DISTANCE");
                anchor=fill; cycleLiters=BigDecimal.ZERO; trailing=false;
            }
        }
        if(trailing) warnings.add("TRAILING_PARTIAL_FILLS_IGNORED");
        if(cycles==0) warnings.add("NO_COMPLETE_FUEL_CYCLE");
        else warnings.add("MISSING_REFUELINGS_CAN_DISTORT_CONSUMPTION");
        return new Result(allLiters,allCost.setScale(2,RoundingMode.HALF_UP),allLiters.signum()>0?allCost.divide(allLiters,2,RoundingMode.HALF_UP):null,
            measuredKm>0?measuredLiters.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(measuredKm),2,RoundingMode.HALF_UP):null,
            measuredKm,measuredLiters,cycles,List.copyOf(warnings));
    }
}
