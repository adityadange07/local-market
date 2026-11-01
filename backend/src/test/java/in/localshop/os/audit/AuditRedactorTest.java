package in.localshop.os.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AuditRedactorTest {

    private final AuditRedactor redactor = new AuditRedactor(new ObjectMapper());

    @Test
    void redactsSensitiveFieldsRecursivelyAndBoundsLargeSummaries() {
        String sanitized = redactor.redact(Map.of(
                "changedFields", List.of(Map.of("displayName", "Shop", "phone", "+919999999999")),
                "accessToken", "secret-value",
                "orderStatus", "READY"));

        assertThat(sanitized).contains("[REDACTED]").contains("READY").doesNotContain("+919999999999", "secret-value");
        assertThat(redactor.redact(Map.of("large", java.util.Collections.nCopies(40, "x".repeat(250)))))
                .contains("SIZE_LIMIT").doesNotContain("x".repeat(300));
    }
}
