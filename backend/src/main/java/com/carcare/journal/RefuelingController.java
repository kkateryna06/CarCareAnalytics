package com.carcare.journal;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.LocalDate;
import static com.carcare.journal.JournalTypes.*;
import static com.carcare.journal.JournalDtos.*;

@RestController @RequestMapping("/api/v1")
public class RefuelingController {
    private final JournalService service;
    private final JournalQueryService queries;
    private final JournalRequests requests;
    public RefuelingController(JournalService service,JournalQueryService queries,JournalRequests requests) { this.service=service; this.queries=queries; this.requests=requests; }
    private long user(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
    @GetMapping("/vehicles/{vehicleId}/refuelings")
    public JournalPage<RefuelingResponse> list(@AuthenticationPrincipal Jwt jwt,@PathVariable long vehicleId,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="date,desc") String sort,
        @RequestParam(required=false) LocalDate dateFrom,@RequestParam(required=false) LocalDate dateTo,
        @RequestParam(required=false) Category category,@RequestParam(required=false) DocumentType type) {
        var result=queries.list(user(jwt),vehicleId,Kind.REFUELING,page,size,sort,dateFrom,dateTo,category,type);
        return new JournalPage<>(result.content().stream().map(RefuelingResponse.class::cast).toList(),page,size,result.totalElements(),result.totalPages());
    }
    @PostMapping("/vehicles/{vehicleId}/refuelings")
    public ResponseEntity<RefuelingResponse> create(@AuthenticationPrincipal Jwt jwt,@PathVariable long vehicleId,@RequestBody RefuelingWrite data,@RequestHeader(name="Idempotency-Key",required=false) String key) {
        var result=(RefuelingResponse)service.create(user(jwt),vehicleId,Kind.REFUELING,requests.tree(data),key);
        return ResponseEntity.created(URI.create("/api/v1/refuelings/"+result.id())).body(result);
    }
    @GetMapping("/refuelings/{id}")
    public RefuelingResponse get(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { return (RefuelingResponse)service.get(user(jwt),Kind.REFUELING,id); }
    @PatchMapping("/refuelings/{id}")
    public RefuelingResponse patch(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@RequestBody JsonNode body) { return (RefuelingResponse)service.patch(user(jwt),Kind.REFUELING,id,body); }
    @DeleteMapping("/refuelings/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { service.delete(user(jwt),Kind.REFUELING,id); return ResponseEntity.noContent().build(); }
}
