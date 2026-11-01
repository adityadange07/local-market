package in.localshop.os.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ApiError(
        String code,
        String message,
        Map<String, String> fieldErrors,
        String traceId) {

    public ApiError {
        fieldErrors = fieldErrors == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }
}
