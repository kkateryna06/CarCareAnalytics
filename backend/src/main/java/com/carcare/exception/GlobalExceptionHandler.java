package com.carcare.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import java.time.Instant;
import java.util.LinkedHashMap;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException ex, HttpServletRequest req) {
        return ResponseEntity.status(ex.status()).body(new ApiError(ex.code(), ex.getMessage(), ex.fieldErrors(), Instant.now(), req.getRequestURI()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        var fields = new LinkedHashMap<String, String>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return api(ApiException.invalid(fields), req);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, ConstraintViolationException.class,
        MissingServletRequestParameterException.class, MissingRequestHeaderException.class})
    ResponseEntity<ApiError> badRequest(Exception ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(ApiError.of("BAD_REQUEST", "Некорректный формат запроса", req.getRequestURI()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException ex, HttpServletRequest req) {
        return ResponseEntity.status(409).body(ApiError.of("CONFLICT", "Конфликт сохраняемых данных", req.getRequestURI()));
    }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> missing(NoResourceFoundException ex, HttpServletRequest req) {
        return api(ApiException.notFound(), req);
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return ResponseEntity.status(405).body(ApiError.of("METHOD_NOT_ALLOWED", "Метод не поддерживается", req.getRequestURI()));
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> unsupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return ResponseEntity.status(415).body(ApiError.of("UNSUPPORTED_MEDIA_TYPE", "Используйте Content-Type: application/json", req.getRequestURI()));
    }
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<Void> unacceptable(HttpMediaTypeNotAcceptableException ex) {
        // Do not attempt another JSON serialization when the client explicitly rejects JSON.
        return ResponseEntity.status(406).build();
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest req) {
        // Do not log request bodies, credentials, JWTs or database exception messages.
        log.error("Unhandled API failure: {}", ex.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(ApiError.of("INTERNAL_ERROR", "Внутренняя ошибка сервера", req.getRequestURI()));
    }
}
