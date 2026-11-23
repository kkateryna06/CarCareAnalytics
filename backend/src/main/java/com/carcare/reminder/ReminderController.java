package com.carcare.reminder;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static com.carcare.reminder.ReminderDtos.*;

@RestController @RequestMapping("/api/v1")
public class ReminderController {
    private final ReminderService service;
    public ReminderController(ReminderService service) { this.service=service; }
    @GetMapping("/vehicles/{vehicleId}/reminders") public RemindersResponse list(@AuthenticationPrincipal Jwt jwt,@PathVariable long vehicleId,@RequestParam(defaultValue="UTC") String timeZone,@RequestParam(defaultValue="false") boolean includeDone) {
        return service.list(Long.parseLong(jwt.getSubject()),vehicleId,timeZone,includeDone);
    }
    @PatchMapping("/services/{id}/reminder") public ResponseEntity<Void> complete(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody Completion completion) {
        service.complete(Long.parseLong(jwt.getSubject()),id,completion.done()); return ResponseEntity.noContent().build();
    }
}
