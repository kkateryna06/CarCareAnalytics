package com.carcare.exception;

import org.springframework.http.HttpStatus;
import java.util.Map;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fieldErrors;
    public ApiException(HttpStatus status, String code, String message) { this(status, code, message, Map.of()); }
    public ApiException(HttpStatus status, String code, String message, Map<String, String> fields) {
        super(message); this.status = status; this.code = code; this.fieldErrors = fields;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Map<String, String> fieldErrors() { return fieldErrors; }
    public static ApiException invalid(Map<String, String> fields) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Проверьте введённые данные", fields);
    }
    public static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Объект не найден"); }
}
