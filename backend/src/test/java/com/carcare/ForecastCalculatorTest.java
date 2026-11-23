package com.carcare;

import com.carcare.forecast.*;
import com.carcare.export.CsvEncoder;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ForecastCalculatorTest {
    private ForecastDtos.Forecast forecast(String... values) {
        var today = LocalDate.of(2026, 11, 12); var start = YearMonth.from(today).minusMonths(values.length);
        var history = new ArrayList<ForecastDtos.Month>();
        for (int i = 0; i < values.length; i++) history.add(new ForecastDtos.Month(start.plusMonths(i).toString(), new BigDecimal(values[i])));
        return ForecastCalculator.calculate(1, today, "Europe/Warsaw", 12, history);
    }
    @Test void threeMonthsUseExactWeightsAndNextCalendarMonth() {
        var result = forecast("100", "200", "300");
        assertThat(result.targetMonth()).isEqualTo("2026-12"); assertThat(result.excludedMonth()).isEqualTo("2026-11");
        assertThat(result.horizonMonths()).isEqualTo(2); assertThat(result.sufficientData()).isTrue();
        assertThat(result.methods().get(0).forecastPln()).isEqualByComparingTo("200.00");
        assertThat(result.methods().get(1).forecastPln()).isEqualByComparingTo("233.33");
        assertThat(result.methods().get(0).maePln()).isNull(); assertThat(result.bestMethod()).isNull();
    }
    @Test void fewerThanThreeMonthsHasNoForecastAndNoMae() {
        for (var result : List.of(forecast(), forecast("10"), forecast("10", "0"))) {
            assertThat(result.sufficientData()).isFalse();
            assertThat(result.methods()).allMatch(m -> m.forecastPln() == null && m.maePln() == null);
            assertThat(result.warnings()).contains("INSUFFICIENT_HISTORY", "NO_BACKTEST_TARGETS");
        }
    }
    @Test void backtestUsesSameHorizonSkipsOriginMonthAndNeverUsesTarget() {
        var result = forecast("100", "200", "300", "9999", "500", "600");
        var first = result.evaluations().getFirst();
        assertThat(first.targetMonth()).isEqualTo("2026-09");
        assertThat(first.trainingFrom()).isEqualTo("2026-05"); assertThat(first.trainingTo()).isEqualTo("2026-07");
        assertThat(first.excludedMonth()).isEqualTo("2026-08"); assertThat(first.simplePln()).isEqualByComparingTo("200");
        assertThat(first.weightedPln()).isEqualByComparingTo("233.33");
        assertThat(first.simpleAbsoluteErrorPln()).isEqualByComparingTo("300");
        assertThat(first.weightedAbsoluteErrorPln()).isEqualByComparingTo("266.67");
        var altered = forecast("100", "200", "300", "9999", "1", "2");
        assertThat(altered.evaluations().getFirst().simplePln()).isEqualTo(first.simplePln());
        assertThat(altered.evaluations().getFirst().weightedPln()).isEqualTo(first.weightedPln());
    }
    @Test void trendingSeriesHasHandCalculatedMaeAndWeightedWinner() {
        var result = forecast("100", "200", "300", "400", "500", "600");
        assertThat(result.evaluationCount()).isEqualTo(2);
        assertThat(result.methods().get(0).maePln()).isEqualByComparingTo("300");
        assertThat(result.methods().get(1).maePln()).isEqualByComparingTo("266.67");
        assertThat(result.bestMethod()).isEqualTo("WEIGHTED_MOVING_AVERAGE");
    }
    @Test void constantsAndZeroHistoryAreValidWithTie() {
        for (String value : List.of("0", "123.45")) {
            var result = forecast(value, value, value, value, value, value, value);
            assertThat(result.methods()).allMatch(m -> m.maePln().signum() == 0);
            assertThat(result.bestMethod()).isEqualTo("TIE");
        }
    }
    @Test void simpleMethodCanWinAndLargeDecimalsRemainExact() {
        assertThat(forecast("100", "100", "100", "100", "1000", "100", "100").bestMethod()).isEqualTo("SIMPLE_MOVING_AVERAGE");
        assertThat(forecast("9999999999.99", "9999999999.99", "9999999999.99").methods().get(0).forecastPln()).isEqualByComparingTo("9999999999.99");
    }
    @Test void malformedGapsAndCurrentMonthAreRejected() {
        var today = LocalDate.of(2026, 11, 12);
        for (var history : List.of(List.of(new ForecastDtos.Month("2026-11", BigDecimal.ONE)), List.of(new ForecastDtos.Month("2026-08", BigDecimal.ONE), new ForecastDtos.Month("2026-10", BigDecimal.ONE))))
            assertThatThrownBy(() -> ForecastCalculator.calculate(1, today, "UTC", 12, history)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void csvQuotesUnicodeNewlinesAndNeutralizesFormulas() {
        var bytes = CsvEncoder.encode(List.of(Arrays.asList("Сервис, \"масло\"\nфильтр", "=1+1", "  @SUM(1)", "100.20", null)));
        assertThat(bytes).startsWith((byte)0xef, (byte)0xbb, (byte)0xbf);
        assertThat(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).contains("\"Сервис, \"\"масло\"\"\nфильтр\"", "\"'=1+1\"", "\"'  @SUM(1)\"", "\"100.20\"", "\"\"\r\n");
    }
}
