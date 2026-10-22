package com.example.securityfoundation.error;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> invalidCredentials() {
        return problem(HttpStatus.UNAUTHORIZED, "Incorrect email or password");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalidRequest() {
        // Binding exceptions may contain rejected passwords; never serialize them directly.
        return problem(HttpStatus.BAD_REQUEST, "Provide a valid email and password (at most 72 UTF-8 bytes)");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
