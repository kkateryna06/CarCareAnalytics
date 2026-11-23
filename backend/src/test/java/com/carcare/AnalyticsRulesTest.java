package com.carcare;

import com.carcare.analytics.FuelCalculator;
import com.carcare.reminder.ReminderRules;
import com.carcare.reminder.ReminderDtos.Status;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class AnalyticsRulesTest {
    private FuelCalculator.Fill fill(int km,String liters,boolean full) { return new FuelCalculator.Fill(km,new BigDecimal(liters),new BigDecimal(liters).multiply(new BigDecimal("6")),full); }
    @Test void fullToFullIncludesIntermediatePartialsAndExcludesStartingFill() {
        var result=FuelCalculator.calculate(List.of(fill(100000,"45",true),fill(100200,"20",false),fill(100600,"30",true)));
        assertThat(result.averageLitersPer100Km()).isEqualByComparingTo("8.33"); assertThat(result.measuredLiters()).isEqualByComparingTo("50"); assertThat(result.measuredDistanceKm()).isEqualTo(600);
    }
    @Test void multipleCyclesAreWeightedByDistance() {
        var result=FuelCalculator.calculate(List.of(fill(0,"50",true),fill(100,"10",true),fill(1000,"45",true)));
        assertThat(result.averageLitersPer100Km()).isEqualByComparingTo("5.50"); assertThat(result.cycleCount()).isEqualTo(2);
    }
    @Test void incompleteEdgesAndZeroDistanceDoNotBecomeFakeConsumption() {
        var result=FuelCalculator.calculate(List.of(fill(100,"10",false),fill(200,"20",true),fill(200,"10",true),fill(300,"5",false)));
        assertThat(result.averageLitersPer100Km()).isNull(); assertThat(result.cycleCount()).isZero();
        assertThat(result.warnings()).contains("LEADING_PARTIAL_FILLS_IGNORED","TRAILING_PARTIAL_FILLS_IGNORED","NON_POSITIVE_FUEL_DISTANCE","NO_COMPLETE_FUEL_CYCLE");
    }
    @Test void noFuelReturnsNullRatesAndZeroTotals() {
        var result=FuelCalculator.calculate(List.of()); assertThat(result.averagePricePerLiterPln()).isNull(); assertThat(result.averageLitersPer100Km()).isNull(); assertThat(result.totalCostPln()).isEqualByComparingTo("0.00");
    }
    @Test void deadlinesUseInclusiveNotificationThresholds() {
        var today=LocalDate.of(2026,11,11);
        for(int days:new int[]{30,7,1,0,-1,31}) {
            var r=ReminderRules.evaluate("DOCUMENT",1,"INSURANCE","OC",today.plusDays(days),null,false,today,0);
            assertThat(r.status()).isEqualTo(days<0?Status.OVERDUE:days==0?Status.DUE:days<=30?Status.UPCOMING:Status.SCHEDULED);
            assertThat(r.notificationStage()).isEqualTo(days<0?"DATE_OVERDUE":days==0?"DATE_DUE":days<=1?"D1":days<=7?"D7":days<=30?"D30":null);
        }
    }
    @Test void mileageAndDateUseMostUrgentCriterionAndDoneSuppressesNotification() {
        var today=LocalDate.of(2026,11,11);
        var r=ReminderRules.evaluate("SERVICE",1,"OIL_CHANGE","Oil",today.plusDays(100),10100,false,today,10000);
        assertThat(r.status()).isEqualTo(Status.UPCOMING); assertThat(r.notificationStage()).isEqualTo("K100");
        var overdue=ReminderRules.evaluate("SERVICE",1,"OIL_CHANGE","Oil",today.plusDays(100),9999,false,today,10000);
        assertThat(overdue.status()).isEqualTo(Status.OVERDUE);
        var done=ReminderRules.evaluate("SERVICE",1,"OIL_CHANGE","Oil",today.minusDays(1),9999,true,today,10000);
        assertThat(done.status()).isEqualTo(Status.DONE); assertThat(done.notificationKey()).isNull();
    }
    @Test void changedDueConditionProducesNewDeduplicationKey() {
        var today=LocalDate.of(2026,11,11);
        var first=ReminderRules.evaluate("SERVICE",1,"OIL_CHANGE","Oil",today.plusDays(7),null,false,today,10000);
        var changed=ReminderRules.evaluate("SERVICE",1,"OIL_CHANGE","Oil",today.plusDays(6),null,false,today,10000);
        assertThat(first.notificationKey()).isNotEqualTo(changed.notificationKey());
    }
}
