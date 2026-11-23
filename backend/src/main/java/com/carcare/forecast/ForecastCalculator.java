package com.carcare.forecast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import static com.carcare.forecast.ForecastDtos.*;
import com.carcare.forecast.ForecastDtos.Month;

/** Fixed three-month baselines. Month t+1 uses t-3..t-1; incomplete month t is excluded. */
public final class ForecastCalculator {
    private ForecastCalculator() {}
    private static BigDecimal mean(List<Month> months, int start, boolean weighted) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int j = 0; j < 3; j++) sum = sum.add(months.get(start + j).amountPln().multiply(BigDecimal.valueOf(weighted ? j + 1 : 1)));
        return sum.divide(BigDecimal.valueOf(weighted ? 6 : 3), 2, RoundingMode.HALF_UP);
    }
    public static Forecast calculate(long vehicle, LocalDate today, String zone, int requested, List<Month> history) {
        var current = YearMonth.from(today);
        // Reject inconsistent caller data rather than silently training across gaps or on future months.
        for (int i = 0; i < history.size(); i++) {
            var month = YearMonth.parse(history.get(i).month());
            if (month.compareTo(current) >= 0 || history.get(i).amountPln().signum() < 0 ||
                (i > 0 && !month.equals(YearMonth.parse(history.get(i - 1).month()).plusMonths(1))))
                throw new IllegalArgumentException("History must contain consecutive completed months and nonnegative amounts");
        }
        if (!history.isEmpty() && !YearMonth.parse(history.getLast().month()).equals(current.minusMonths(1)))
            throw new IllegalArgumentException("History must end at the last completed month");
        var evaluations = new ArrayList<Evaluation>();
        // Direct horizon=2: at each historical origin skip its unfinished month as in the live forecast.
        for (int target = 4; target < history.size(); target++) {
            int start = target - 4;
            var simple = mean(history, start, false); var weighted = mean(history, start, true);
            var actual = history.get(target).amountPln();
            evaluations.add(new Evaluation(history.get(target).month(), history.get(start).month(), history.get(start + 2).month(),
                history.get(target - 1).month(), actual, simple, weighted, actual.subtract(simple).abs(), actual.subtract(weighted).abs()));
        }
        BigDecimal simpleMae = null, weightedMae = null;
        if (!evaluations.isEmpty()) {
            simpleMae = evaluations.stream().map(Evaluation::simpleAbsoluteErrorPln).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(evaluations.size()), 2, RoundingMode.HALF_UP);
            weightedMae = evaluations.stream().map(Evaluation::weightedAbsoluteErrorPln).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(evaluations.size()), 2, RoundingMode.HALF_UP);
        }
        boolean enough = history.size() >= 3;
        var methods = List.of(new Method("SIMPLE_MOVING_AVERAGE", enough ? mean(history, history.size() - 3, false) : null, simpleMae),
            new Method("WEIGHTED_MOVING_AVERAGE", enough ? mean(history, history.size() - 3, true) : null, weightedMae));
        String best = simpleMae == null ? null : simpleMae.compareTo(weightedMae) == 0 ? "TIE" :
            simpleMae.compareTo(weightedMae) < 0 ? "SIMPLE_MOVING_AVERAGE" : "WEIGHTED_MOVING_AVERAGE";
        var warnings = new ArrayList<>(List.of("CURRENT_MONTH_EXCLUDED", "HISTORY_COMPLETENESS_NOT_VERIFIED", "RARE_REPAIRS_NOT_PREDICTABLE"));
        if (!enough) warnings.add("INSUFFICIENT_HISTORY");
        if (evaluations.isEmpty()) warnings.add("NO_BACKTEST_TARGETS");
        else if (history.size() < 6 || evaluations.size() < 3) warnings.add("SMALL_EVALUATION_SAMPLE");
        if (history.stream().anyMatch(m -> m.amountPln().signum() == 0)) warnings.add("ZERO_MONTHS_ASSUMED_NO_EXPENSES");
        return new Forecast(vehicle, today, zone, current.plusMonths(1).toString(), current.toString(), 3, 2, history.size(), requested,
            enough, evaluations.size(), best, List.copyOf(history), methods, List.copyOf(evaluations), List.copyOf(warnings));
    }
}
