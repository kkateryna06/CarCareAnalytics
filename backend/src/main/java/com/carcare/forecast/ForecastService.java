package com.carcare.forecast;

import com.carcare.exception.ApiException;
import com.carcare.reminder.ReminderService;
import com.carcare.vehicle.VehicleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.carcare.forecast.ForecastDtos.*;
import com.carcare.forecast.ForecastDtos.Month;

@Service
public class ForecastService {
    private final VehicleRepository vehicles;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public ForecastService(VehicleRepository vehicles, JdbcTemplate jdbc, Clock clock) {
        this.vehicles = vehicles; this.jdbc = jdbc; this.clock = clock;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Forecast forecast(long user, long vehicle, String zone, int historyMonths) {
        vehicles.findByIdAndOwnerId(vehicle, user).orElseThrow(ApiException::notFound);
        if (historyMonths < 3 || historyMonths > 60) throw ApiException.invalid(Map.of("historyMonths", "Выберите от 3 до 60 месяцев"));
        var today = LocalDate.now(clock.withZone(ReminderService.zone(zone)));
        var current = YearMonth.from(today);
        LocalDate first = jdbc.queryForObject("SELECT min(date) FROM expenses WHERE vehicle_id=? AND date<?", LocalDate.class, vehicle, current.atDay(1));
        var history = new ArrayList<Month>();
        if (first != null) {
            var start = YearMonth.from(first);
            if (start.isBefore(current.minusMonths(historyMonths))) start = current.minusMonths(historyMonths);
            var totals = new HashMap<YearMonth, BigDecimal>();
            jdbc.query("SELECT date_trunc('month',date)::date,sum(amount_pln) FROM expenses WHERE vehicle_id=? AND date>=? AND date<? GROUP BY 1",
                rs -> { totals.put(YearMonth.from(rs.getObject(1, LocalDate.class)), rs.getBigDecimal(2)); }, vehicle, start.atDay(1), current.atDay(1));
            for (var month = start; month.isBefore(current); month = month.plusMonths(1))
                history.add(new Month(month.toString(), totals.getOrDefault(month, BigDecimal.ZERO).setScale(2)));
        }
        return ForecastCalculator.calculate(vehicle, today, zone, historyMonths, history);
    }
}
