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
public class OdometerController {
    private final JournalService service;
    private final JournalQueryService queries;
    private final JournalRequests requests;
    public OdometerController(JournalService service,JournalQueryService queries,JournalRequests requests) { this.service=service; this.queries=queries; this.requests=requests; }
    private long user(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
    @GetMapping("/vehicles/{vehicleId}/odometer")
    public JournalPage<OdometerResponse> list(@AuthenticationPrincipal Jwt jwt,@PathVariable long vehicleId,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="date,desc") String sort,
        @RequestParam(required=false) LocalDate dateFrom,@RequestParam(required=false) LocalDate dateTo,
        @RequestParam(required=false) Category category,@RequestParam(required=false) DocumentType type) {
        var result=queries.list(user(jwt),vehicleId,Kind.ODOMETER,page,size,sort,dateFrom,dateTo,category,type);
        return new JournalPage<>(result.content().stream().map(OdometerResponse.class::cast).toList(),page,size,result.totalElements(),result.totalPages());
    }
    @PostMapping("/vehicles/{vehicleId}/odometer")
    public ResponseEntity<OdometerResponse> create(@AuthenticationPrincipal Jwt jwt,@PathVariable long vehicleId,@RequestBody OdometerWrite data,@RequestHeader(name="Idempotency-Key",required=false) String key) {
        var result=(OdometerResponse)service.create(user(jwt),vehicleId,Kind.ODOMETER,requests.tree(data),key);
        return ResponseEntity.created(URI.create("/api/v1/odometer/"+result.id())).body(result);
    }
    @GetMapping("/odometer/{id}")
    public OdometerResponse get(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { return (OdometerResponse)service.get(user(jwt),Kind.ODOMETER,id); }
    @PatchMapping("/odometer/{id}")
    public OdometerResponse patch(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@RequestBody JsonNode body) { return (OdometerResponse)service.patch(user(jwt),Kind.ODOMETER,id,body); }
    @DeleteMapping("/odometer/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { service.delete(user(jwt),Kind.ODOMETER,id); return ResponseEntity.noContent().build(); }
}
