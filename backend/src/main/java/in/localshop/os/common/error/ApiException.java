package in.localshop.os.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;

/** An expected application error with an intentionally safe public message. */
public final class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ApiErrorCode code;
    private final Map<String, String> fieldErrors;

    public ApiException(HttpStatus status, ApiErrorCode code, String publicMessage) {
        this(status, code, publicMessage, Map.of());
    }

    public ApiException(
            HttpStatus status,
            ApiErrorCode code,
            String publicMessage,
            Map<String, String> fieldErrors) {
        super(Objects.requireNonNull(publicMessage, "publicMessage"));
        this.status = Objects.requireNonNull(status, "status");
        this.code = Objects.requireNonNull(code, "code");
        this.fieldErrors = fieldErrors == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public HttpStatus status() {
        return status;
    }

    public ApiErrorCode code() {
        return code;
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
