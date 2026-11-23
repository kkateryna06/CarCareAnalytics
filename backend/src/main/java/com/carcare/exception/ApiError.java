package com.carcare.exception;

import java.time.Instant;
import java.util.Map;

public record ApiError(String code, String message, Map<String, String> fieldErrors, Instant timestamp, String path) {
    public static ApiError of(String code, String message, String path) {
        return new ApiError(code, message, Map.of(), Instant.now(), path);
    }
}
