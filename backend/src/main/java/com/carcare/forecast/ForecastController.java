package com.carcare.forecast;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}/forecast")
public class ForecastController {
    private final ForecastService service;
    public ForecastController(ForecastService service) { this.service = service; }
    @GetMapping
    public ForecastDtos.Forecast forecast(@AuthenticationPrincipal Jwt jwt, @PathVariable long vehicleId,
        @RequestParam(defaultValue = "UTC") String timeZone, @RequestParam(defaultValue = "12") int historyMonths) {
        return service.forecast(Long.parseLong(jwt.getSubject()), vehicleId, timeZone, historyMonths);
    }
}
