package com.HotelManagement.AirBnb.shared.exception;

import com.HotelManagement.AirBnb.payment.exception.PaymentFailedException;
import com.HotelManagement.AirBnb.payment.gateway.PaymentGatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorized(UnauthorizedException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "You are not allowed to perform this action");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(BadRequestException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleUnavailable(ServiceUnavailableException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<Map<String, String>> handlePaymentFailed(PaymentFailedException ex) {
        return error(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
    }

    /** Gateway problems: details are logged by the gateway client, never returned. */
    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<Map<String, String>> handleGateway(PaymentGatewayException ex) {
        return error(HttpStatus.BAD_GATEWAY, "The payment provider couldn't complete the request. Nothing was changed; please try again.");
    }

    /** Bean-validation failures on @Valid request bodies/parts: { error, fieldErrors: { field: message } }. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Please correct the highlighted fields");
        body.put("fieldErrors", fieldErrors);
        log.debug("Responding 400: validation failed for {}", fieldErrors.keySet());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, String>> handleMethodValidation(HandlerMethodValidationException ex) {
        return error(HttpStatus.BAD_REQUEST, "Invalid request parameters");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> handleUnreadable(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "Malformed request");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return error(HttpStatus.CONTENT_TOO_LARGE, "Upload is too large. Each photo must be 5 MB or smaller.");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> handleMultipart(MultipartException ex) {
        return error(HttpStatus.BAD_REQUEST, "Invalid file upload");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, "The request conflicts with existing data");
    }

    /**
     * Framework exceptions that already carry a status (404 no handler, 405, 415, missing params...)
     * keep it; anything else is a 500. Never leak internal messages or stack traces to clients.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            return error(HttpStatus.valueOf(er.getStatusCode().value()), "Invalid request");
        }
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        if (status.is5xxServerError() || status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            log.warn("Responding {}: {}", status.value(), message);
        } else {
            log.debug("Responding {}: {}", status.value(), message);
        }
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
