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
public class ExpenseController {
    private final JournalService service;
    private final JournalQueryService queries;
    private final JournalRequests requests;
    public ExpenseController(JournalService service,JournalQueryService queries,JournalRequests requests) { this.service=service; this.queries=queries; this.requests=requests; }
    private long user(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
    @GetMapping("/vehicles/{vehicleId}/expenses")
    public JournalPage<ExpenseResponse> list(@AuthenticationPrincipal Jwt jwt,@PathVariable long vehicleId,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="date,desc") String sort,
        @RequestParam(required=false) LocalDate dateFrom,@RequestParam(required=false) LocalDate dateTo,
        @RequestParam(required=false) Category category,@RequestParam(required=false) DocumentType type) {
        var result=queries.list(user(jwt),vehicleId,Kind.EXPENSE,page,size,sort,dateFrom,dateTo,category,type);
        return new JournalPage<>(result.content().stream().map(ExpenseResponse.class::cast).toList(),page,size,result.totalElements(),result.totalPages());
    }
    @PostMapping("/vehicles/{vehicleId}/expenses")
    public ResponseEntity<ExpenseResponse> create(@AuthenticationPrincipal Jwt jwt,@PathVariable long vehicleId,@RequestBody ExpenseWrite data,@RequestHeader(name="Idempotency-Key",required=false) String key) {
        var result=(ExpenseResponse)service.create(user(jwt),vehicleId,Kind.EXPENSE,requests.tree(data),key);
        return ResponseEntity.created(URI.create("/api/v1/expenses/"+result.id())).body(result);
    }
    @GetMapping("/expenses/{id}")
    public ExpenseResponse get(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { return (ExpenseResponse)service.get(user(jwt),Kind.EXPENSE,id); }
    @PatchMapping("/expenses/{id}")
    public ExpenseResponse patch(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@RequestBody JsonNode body) { return (ExpenseResponse)service.patch(user(jwt),Kind.EXPENSE,id,body); }
    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { service.delete(user(jwt),Kind.EXPENSE,id); return ResponseEntity.noContent().build(); }
}
