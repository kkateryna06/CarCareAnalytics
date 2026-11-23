package com.carcare.export;

import com.carcare.journal.JournalTypes.Category;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}/expenses/export.csv")
public class ExpenseExportController {
    private final ExpenseExportService service;
    public ExpenseExportController(ExpenseExportService service) { this.service = service; }
    @GetMapping(produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt, @PathVariable long vehicleId,
        @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
        @RequestParam(defaultValue = "UTC") String timeZone, @RequestParam(required = false) Category category) {
        byte[] csv = service.export(Long.parseLong(jwt.getSubject()), vehicleId, dateFrom, dateTo, timeZone, category);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"carcare-expenses-" + vehicleId + ".csv\"")
            .cacheControl(CacheControl.noStore()).contentLength(csv.length).body(csv);
    }
}
