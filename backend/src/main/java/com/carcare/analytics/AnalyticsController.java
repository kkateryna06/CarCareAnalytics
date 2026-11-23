package com.carcare.analytics;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import static com.carcare.analytics.AnalyticsDtos.*;

@RestController @RequestMapping("/api/v1/vehicles/{vehicleId}/analytics")
public class AnalyticsController {
    private final AnalyticsService service;
    public AnalyticsController(AnalyticsService service) { this.service=service; }
    @GetMapping("/summary") public Summary summary(@AuthenticationPrincipal Jwt jwt,@PathVariable long vehicleId,@RequestParam(required=false) LocalDate dateFrom,@RequestParam(required=false) LocalDate dateTo,@RequestParam(defaultValue="UTC") String timeZone) {
        return service.summary(Long.parseLong(jwt.getSubject()),vehicleId,service.period(dateFrom,dateTo,timeZone));
    }
    @GetMapping("/categories") public Categories categories(@AuthenticationPrincipal Jwt jwt,@PathVariable long vehicleId,@RequestParam(required=false) LocalDate dateFrom,@RequestParam(required=false) LocalDate dateTo,@RequestParam(defaultValue="UTC") String timeZone) {
        return service.categories(Long.parseLong(jwt.getSubject()),vehicleId,service.period(dateFrom,dateTo,timeZone));
    }
    @GetMapping("/monthly") public Monthly monthly(@AuthenticationPrincipal Jwt jwt,@PathVariable long vehicleId,@RequestParam(required=false) LocalDate dateFrom,@RequestParam(required=false) LocalDate dateTo,@RequestParam(defaultValue="UTC") String timeZone) {
        return service.monthly(Long.parseLong(jwt.getSubject()),vehicleId,service.period(dateFrom,dateTo,timeZone));
    }
    @GetMapping("/fuel") public Fuel fuel(@AuthenticationPrincipal Jwt jwt,@PathVariable long vehicleId,@RequestParam(required=false) LocalDate dateFrom,@RequestParam(required=false) LocalDate dateTo,@RequestParam(defaultValue="UTC") String timeZone) {
        return service.fuel(Long.parseLong(jwt.getSubject()),vehicleId,service.period(dateFrom,dateTo,timeZone));
    }
    @GetMapping("/dashboard") public Dashboard dashboard(@AuthenticationPrincipal Jwt jwt,@PathVariable long vehicleId,@RequestParam(required=false) LocalDate dateFrom,@RequestParam(required=false) LocalDate dateTo,@RequestParam(defaultValue="UTC") String timeZone) {
        return service.dashboard(Long.parseLong(jwt.getSubject()),vehicleId,service.period(dateFrom,dateTo,timeZone),timeZone);
    }
}
