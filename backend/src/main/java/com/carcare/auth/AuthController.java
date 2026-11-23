package com.carcare.auth;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirements
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody AuthRequest request) { return service.register(request); }
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest request) { return service.login(request); }
}
