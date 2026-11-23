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
public class DocumentController {
    private final JournalService service;
    private final JournalQueryService queries;
    private final JournalRequests requests;
    public DocumentController(JournalService service,JournalQueryService queries,JournalRequests requests) { this.service=service; this.queries=queries; this.requests=requests; }
    private long user(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
    @GetMapping("/vehicles/{vehicleId}/documents")
    public JournalPage<DocumentResponse> list(@AuthenticationPrincipal Jwt jwt,@PathVariable long vehicleId,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="date,desc") String sort,
        @RequestParam(required=false) LocalDate dateFrom,@RequestParam(required=false) LocalDate dateTo,
        @RequestParam(required=false) Category category,@RequestParam(required=false) DocumentType type) {
        var result=queries.list(user(jwt),vehicleId,Kind.DOCUMENT,page,size,sort,dateFrom,dateTo,category,type);
        return new JournalPage<>(result.content().stream().map(DocumentResponse.class::cast).toList(),page,size,result.totalElements(),result.totalPages());
    }
    @PostMapping("/vehicles/{vehicleId}/documents")
    public ResponseEntity<DocumentResponse> create(@AuthenticationPrincipal Jwt jwt,@PathVariable long vehicleId,@RequestBody DocumentWrite data,@RequestHeader(name="Idempotency-Key",required=false) String key) {
        var result=(DocumentResponse)service.create(user(jwt),vehicleId,Kind.DOCUMENT,requests.tree(data),key);
        return ResponseEntity.created(URI.create("/api/v1/documents/"+result.id())).body(result);
    }
    @GetMapping("/documents/{id}")
    public DocumentResponse get(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { return (DocumentResponse)service.get(user(jwt),Kind.DOCUMENT,id); }
    @PatchMapping("/documents/{id}")
    public DocumentResponse patch(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@RequestBody JsonNode body) { return (DocumentResponse)service.patch(user(jwt),Kind.DOCUMENT,id,body); }
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { service.delete(user(jwt),Kind.DOCUMENT,id); return ResponseEntity.noContent().build(); }
}
