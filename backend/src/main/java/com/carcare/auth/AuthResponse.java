package com.carcare.auth;

import com.carcare.user.UserResponse;
import java.time.Instant;

public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {}
