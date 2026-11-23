package com.carcare.vehicle;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/vehicles")
public class VehicleController {
    private final VehicleService service;
    public VehicleController(VehicleService service) { this.service = service; }
    private long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
    @GetMapping
    public VehiclePage list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.list(userId(jwt), page, size, sort);
    }
    @GetMapping("/{id}")
    public VehicleResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) { return service.get(userId(jwt), id); }
    @GetMapping(value = "/{id}/photo", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestParam UUID version) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).body(service.photo(userId(jwt), id, version));
    }
    @PostMapping
    public ResponseEntity<VehicleResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody VehicleWrite request) {
        var result = service.create(userId(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/vehicles/" + result.id())).body(result);
    }
    @PatchMapping("/{id}")
    public VehicleResponse patch(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody JsonNode patch) { return service.patch(userId(jwt), id, patch); }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        service.delete(userId(jwt), id); return ResponseEntity.noContent().build();
    }
}
