package com.abhedyam.exception;

import com.abhedyam.dto.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        String correlationId = MDC.get("correlationId");
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        
        ErrorResponse error = new ErrorResponse(
            "Resource Not Found",
            ex.getMessage(),
            "RESOURCE_NOT_FOUND",
            correlationId,
            path
        );
        
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, WebRequest request) {
        String correlationId = MDC.get("correlationId");
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        
        ErrorResponse error = new ErrorResponse(
            "Business Error",
            ex.getMessage(),
            ex.getErrorCode(),
            correlationId,
            path
        );
        
        // "Not your customer" is a permission problem (403), not a dead session: apps log the owner out on 401.
        HttpStatus status = "UNAUTHORIZED".equals(ex.getErrorCode()) 
            ? HttpStatus.FORBIDDEN 
            : "RATE_LIMITED".equals(ex.getErrorCode())
                ? HttpStatus.TOO_MANY_REQUESTS
                : "STALE_EDIT".equals(ex.getErrorCode())
                    ? HttpStatus.CONFLICT
                    : "SESSION_REVOKED".equals(ex.getErrorCode())
                        ? HttpStatus.UNAUTHORIZED // logged out from another phone: the app goes to sign in
                        : HttpStatus.BAD_REQUEST;
        
        log.warn("Business error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(status).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, WebRequest request) {
        String correlationId = MDC.get("correlationId");
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        String message = errors.entrySet().stream()
            .map(e -> e.getKey() + ": " + e.getValue())
            .collect(Collectors.joining(", "));
        
        ErrorResponse error = new ErrorResponse(
            "Validation Error",
            message,
            "VALIDATION_ERROR",
            correlationId,
            path
        );
        
        log.warn("Validation error: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        String correlationId = MDC.get("correlationId");
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        
        String message = ex.getConstraintViolations().stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.joining(", "));
        
        ErrorResponse error = new ErrorResponse(
            "Validation Error",
            message,
            "VALIDATION_ERROR",
            correlationId,
            path
        );
        
        log.warn("Constraint violation: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex, WebRequest request) {
        String correlationId = MDC.get("correlationId");
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        
        String errorMessage = ex.getMessage();
        String userMessage = "Duplicate data detected. This record already exists.";
        String errorCode = "DUPLICATE_DATA";
        
        if (errorMessage != null) {
            if (errorMessage.contains("Duplicate entry") || errorMessage.contains("duplicate key")) {
                Pattern pattern = Pattern.compile("Duplicate entry '([^']+)' for key '([^']+)'");
                Matcher matcher = pattern.matcher(errorMessage);
                if (matcher.find()) {
                    String duplicateValue = matcher.group(1);
                    String keyName = matcher.group(2);
                    
                    if (keyName.contains("email") || keyName.contains("EMAIL")) {
                        userMessage = "This email address is already registered. Please use a different email.";
                        errorCode = "DUPLICATE_EMAIL";
                    } else if (keyName.contains("phone") || keyName.contains("PHONE")) {
                        userMessage = "This phone number is already registered. Please use a different phone number.";
                        errorCode = "DUPLICATE_PHONE";
                    } else if (keyName.contains("firebase_uid") || keyName.contains("FIREBASE_UID")) {
                        userMessage = "This account is already registered.";
                        errorCode = "DUPLICATE_ACCOUNT";
                    } else if (keyName.contains("code") || keyName.contains("CODE")) {
                        userMessage = "This product code already exists. Please use a different code.";
                        errorCode = "DUPLICATE_PRODUCT_CODE";
                    } else if (keyName.contains("vpa") || keyName.contains("VPA")) {
                        userMessage = "This UPI VPA is already registered. Please use a different VPA.";
                        errorCode = "DUPLICATE_UPI_VPA";
                    } else if (keyName.contains("key") || keyName.contains("KEY")) {
                        userMessage = "This record already exists.";
                        errorCode = "DUPLICATE_RECORD";
                    } else {
                        userMessage = "This record already exists. Please use a different value.";
                    }
                }
            } else if (errorMessage.contains("unique constraint") || errorMessage.contains("UNIQUE constraint")) {
                Pattern pattern = Pattern.compile("unique constraint.*?([a-zA-Z_]+)", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(errorMessage);
                if (matcher.find()) {
                    userMessage = "This value already exists. Please use a different value.";
                }
            }
        }
        
        ErrorResponse error = new ErrorResponse(
            "Duplicate Data Error",
            userMessage,
            errorCode,
            correlationId,
            path
        );
        
        log.warn("Data integrity violation [{}]: {}", errorCode, errorMessage);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        String correlationId = MDC.get("correlationId");
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        
        ErrorResponse error = new ErrorResponse(
            "Unauthorized",
            "Authentication failed. Please provide a valid token.",
            "UNAUTHORIZED",
            correlationId,
            path
        );
        
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
    
    @ExceptionHandler({MaxUploadSizeExceededException.class, FileSizeLimitExceededException.class})
    public ResponseEntity<ErrorResponse> handleFileSizeLimitExceeded(Exception ex, WebRequest request) {
        String correlationId = MDC.get("correlationId");
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        
        String message = "File size exceeds the maximum allowed limit of 50MB. Please upload a smaller image file.";
        
        ErrorResponse error = new ErrorResponse(
            "File Size Limit Exceeded",
            message,
            "FILE_SIZE_LIMIT_EXCEEDED",
            correlationId,
            path
        );
        
        log.warn("File size limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        String correlationId = MDC.get("correlationId");
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        
        ErrorResponse error = new ErrorResponse(
            "Internal Server Error",
            "An unexpected error occurred",
            "INTERNAL_ERROR",
            correlationId,
            path
        );
        
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
