package com.carcare.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ForecastDtos {
    private ForecastDtos() {}
    public record Month(String month, BigDecimal amountPln) {}
    public record Method(String method, BigDecimal forecastPln, BigDecimal maePln) {}
    public record Evaluation(String targetMonth, String trainingFrom, String trainingTo, String excludedMonth,
        BigDecimal actualPln, BigDecimal simplePln, BigDecimal weightedPln,
        BigDecimal simpleAbsoluteErrorPln, BigDecimal weightedAbsoluteErrorPln) {}
    public record Forecast(long vehicleId, LocalDate asOfDate, String timeZone, String targetMonth,
        String excludedMonth, int windowMonths, int horizonMonths, int availableMonths, int requestedHistoryMonths,
        boolean sufficientData, int evaluationCount, String bestMethod, List<Month> history,
        List<Method> methods, List<Evaluation> evaluations, List<String> warnings) {}
}
