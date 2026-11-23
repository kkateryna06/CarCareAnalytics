package com.carcare.journal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import static com.carcare.journal.JournalTypes.*;
import static com.carcare.journal.JournalDtos.*;

@RestController @RequestMapping("/api/v1")
public class JournalController {
    private final JournalQueryService queries;
    public JournalController(JournalQueryService queries) { this.queries=queries; }
    @GetMapping("/vehicles/{vehicleId}/timeline")
    public JournalPage<TimelineItem> timeline(@AuthenticationPrincipal Jwt jwt,@PathVariable long vehicleId,@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="date,desc") String sort,@RequestParam(required=false) LocalDate dateFrom,
        @RequestParam(required=false) LocalDate dateTo,@RequestParam(required=false) Kind kind,@RequestParam(required=false) Category category,
        @RequestParam(defaultValue="false") boolean expensesOnly) {
        return queries.timeline(Long.parseLong(jwt.getSubject()),vehicleId,page,size,sort,dateFrom,dateTo,kind,category,expensesOnly);
    }
}
