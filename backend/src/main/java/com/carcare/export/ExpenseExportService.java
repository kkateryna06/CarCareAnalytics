package com.carcare.export;

import com.carcare.analytics.AnalyticsService;
import com.carcare.exception.ApiException;
import com.carcare.journal.JournalTypes.Category;
import com.carcare.vehicle.VehicleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
import java.util.*;

@Service
public class ExpenseExportService {
    private final VehicleRepository vehicles;
    private final AnalyticsService analytics;
    private final JdbcTemplate jdbc;
    public ExpenseExportService(VehicleRepository vehicles, AnalyticsService analytics, JdbcTemplate jdbc) {
        this.vehicles = vehicles; this.analytics = analytics; this.jdbc = jdbc;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public byte[] export(long user, long vehicle, LocalDate from, LocalDate to, String zone, Category category) {
        var car = vehicles.findByIdAndOwnerId(vehicle, user).orElseThrow(ApiException::notFound).response();
        var period = analytics.period(from, to, zone);
        var args = new ArrayList<Object>(List.of(vehicle, period.dateFrom(), period.dateTo()));
        String filter = category == null ? "" : " AND category=?";
        if (category != null) args.add(category.name());
        var expenses = jdbc.query("SELECT id,date,amount_pln,category,title,description,source_type,source_id FROM expenses WHERE vehicle_id=? AND date BETWEEN ? AND ?" + filter + " ORDER BY date,id LIMIT 25001",
            (rs, n) -> Arrays.asList(rs.getObject("date", LocalDate.class).toString(), rs.getBigDecimal("amount_pln").setScale(2).toPlainString(), "PLN", rs.getString("category"),
                Long.toString(vehicle), car.make(), car.model(), car.registrationNumber(), rs.getString("title"), rs.getString("description"), rs.getString("source_type"), rs.getString("source_id"), rs.getString("id")), args.toArray());
        if (expenses.size() > 25000) throw CsvEncoder.tooLarge();
        var rows = new ArrayList<List<String>>();
        rows.add(List.of("date", "amount_pln", "currency", "category", "vehicle_id", "make", "model", "registration_number", "title", "description", "source_type", "source_id", "expense_id"));
        rows.addAll(expenses);
        return CsvEncoder.encode(rows);
    }
}
