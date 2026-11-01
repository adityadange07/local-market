package in.localshop.os.common.error;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import in.localshop.os.common.web.TraceIdFilter;
import in.localshop.os.identity.IdentityAuthenticationRejectedException;
import in.localshop.os.identity.InvalidOtpPhoneException;
import in.localshop.os.identity.InvalidRefreshTokenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);
    private static final String GENERIC_BAD_REQUEST = "The request could not be processed.";
    private static final String GENERIC_NOT_FOUND = "The requested resource was not found.";
    private static final String GENERIC_INTERNAL_ERROR = "An unexpected error occurred.";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(
            ApiException exception, HttpServletRequest request) {
        return response(
                exception.status(),
                exception.code(),
                exception.getMessage(),
                exception.fieldErrors(),
                request);
    }

    @ExceptionHandler(InvalidOtpPhoneException.class)
    public ResponseEntity<ApiError> handleInvalidOtpPhone(
            InvalidOtpPhoneException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed.", Map.of("phone", "Enter a valid phone number."), request);
    }

    @ExceptionHandler(IdentityAuthenticationRejectedException.class)
    public ResponseEntity<ApiError> handleIdentityAuthenticationRejected(
            IdentityAuthenticationRejectedException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED,
                "The verification code is invalid or no longer valid.", Map.of(), request);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefreshToken(
            InvalidRefreshTokenException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED,
                "Refresh token is invalid or expired.", Map.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed.",
                fieldErrors(exception.getBindingResult()),
                request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        Map<String, String> errors = new TreeMap<>();
        exception.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return response(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed.",
                errors,
                request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleHandlerMethodValidation(
            HandlerMethodValidationException exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed.",
                Map.of(),
                request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleUnreadableRequest(
            Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.INVALID_REQUEST,
                GENERIC_BAD_REQUEST,
                Map.of(),
                request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.INVALID_REQUEST,
                GENERIC_BAD_REQUEST,
                Map.of(exception.getParameterName(), "This parameter is required."),
                request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return response(
                HttpStatus.METHOD_NOT_ALLOWED,
                ApiErrorCode.METHOD_NOT_ALLOWED,
                "The HTTP method is not supported for this resource.",
                Map.of(),
                request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return response(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ApiErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "The request content type is not supported.",
                Map.of(),
                request);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.RESOURCE_NOT_FOUND,
                GENERIC_NOT_FOUND,
                Map.of(),
                request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(
            ResponseStatusException exception, HttpServletRequest request) {
        HttpStatusCode status = exception.getStatusCode();
        return switch (status.value()) {
            case 400, 422 -> response(status, ApiErrorCode.REQUEST_REJECTED,
                    GENERIC_BAD_REQUEST, Map.of(), request);
            case 401 -> response(status, ApiErrorCode.UNAUTHORIZED,
                    "Authentication is required.", Map.of(), request);
            case 403 -> response(status, ApiErrorCode.FORBIDDEN,
                    "You do not have permission to perform this action.", Map.of(), request);
            case 404 -> response(status, ApiErrorCode.RESOURCE_NOT_FOUND,
                    GENERIC_NOT_FOUND, Map.of(), request);
            case 405 -> response(status, ApiErrorCode.METHOD_NOT_ALLOWED,
                    "The HTTP method is not supported for this resource.", Map.of(), request);
            case 409 -> response(status, ApiErrorCode.CONFLICT,
                    "The request conflicts with the current resource state.", Map.of(), request);
            case 415 -> response(status, ApiErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "The request content type is not supported.", Map.of(), request);
            case 429 -> response(status, ApiErrorCode.RATE_LIMITED,
                    "Too many requests. Try again later.", Map.of(), request);
            default -> status.is4xxClientError()
                    ? response(status, ApiErrorCode.REQUEST_REJECTED,
                            GENERIC_BAD_REQUEST, Map.of(), request)
                    : internalError(exception, request);
        };
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        return internalError(exception, request);
    }

    private ResponseEntity<ApiError> internalError(
            Exception exception, HttpServletRequest request) {
        String traceId = traceId(request);
        log.error("Unhandled API exception traceId={} exceptionType={}",
                traceId, exception.getClass().getName());
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorCode.INTERNAL_ERROR,
                GENERIC_INTERNAL_ERROR,
                Map.of(),
                request);
    }

    private ResponseEntity<ApiError> response(
            HttpStatusCode status,
            ApiErrorCode code,
            String message,
            Map<String, String> fieldErrors,
            HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiError(code.name(), message, fieldErrors, traceId(request)));
    }

    private String traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(TraceIdFilter.REQUEST_ATTRIBUTE);
        return traceId instanceof String value ? value : UUID.randomUUID().toString();
    }

    private Map<String, String> fieldErrors(BindingResult bindingResult) {
        Map<String, String> errors = new TreeMap<>();
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            String message = fieldError.getDefaultMessage() == null
                    ? "Invalid value."
                    : fieldError.getDefaultMessage();
            errors.putIfAbsent(fieldError.getField(), message);
        }
        bindingResult.getGlobalErrors().forEach(error ->
                errors.putIfAbsent("_request", error.getDefaultMessage()));
        return errors;
    }
}
