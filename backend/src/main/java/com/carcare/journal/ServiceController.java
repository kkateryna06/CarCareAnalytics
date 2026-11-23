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
public class ServiceController {
    private final JournalService service;
    private final JournalQueryService queries;
    private final JournalRequests requests;
    public ServiceController(JournalService service,JournalQueryService queries,JournalRequests requests) { this.service=service; this.queries=queries; this.requests=requests; }
    private long user(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
    @GetMapping("/vehicles/{vehicleId}/services")
    public JournalPage<ServiceResponse> list(@AuthenticationPrincipal Jwt jwt,@PathVariable long vehicleId,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="date,desc") String sort,
        @RequestParam(required=false) LocalDate dateFrom,@RequestParam(required=false) LocalDate dateTo,
        @RequestParam(required=false) Category category,@RequestParam(required=false) DocumentType type) {
        var result=queries.list(user(jwt),vehicleId,Kind.SERVICE,page,size,sort,dateFrom,dateTo,category,type);
        return new JournalPage<>(result.content().stream().map(ServiceResponse.class::cast).toList(),page,size,result.totalElements(),result.totalPages());
    }
    @PostMapping("/vehicles/{vehicleId}/services")
    public ResponseEntity<ServiceResponse> create(@AuthenticationPrincipal Jwt jwt,@PathVariable long vehicleId,@RequestBody ServiceWrite data,@RequestHeader(name="Idempotency-Key",required=false) String key) {
        var result=(ServiceResponse)service.create(user(jwt),vehicleId,Kind.SERVICE,requests.tree(data),key);
        return ResponseEntity.created(URI.create("/api/v1/services/"+result.id())).body(result);
    }
    @GetMapping("/services/{id}")
    public ServiceResponse get(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { return (ServiceResponse)service.get(user(jwt),Kind.SERVICE,id); }
    @PatchMapping("/services/{id}")
    public ServiceResponse patch(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@RequestBody JsonNode body) { return (ServiceResponse)service.patch(user(jwt),Kind.SERVICE,id,body); }
    @DeleteMapping("/services/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { service.delete(user(jwt),Kind.SERVICE,id); return ResponseEntity.noContent().build(); }
}
