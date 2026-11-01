package in.localshop.os.audit;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class AuditRedactor {

    private static final int MAX_SUMMARY_BYTES = 4096;
    private static final Set<String> SENSITIVE_KEY_PARTS = Set.of(
            "password", "secret", "token", "otp", "code", "phone", "email", "address", "upi",
            "authorization", "name", "customer", "location", "latitude", "longitude", "birth", "ip");
    private final ObjectMapper objectMapper;

    public AuditRedactor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String redact(Map<String, ?> summary) {
        ObjectNode node = objectMapper.valueToTree(summary == null ? Map.of() : summary);
        redactNode(node);
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(node);
            if (serialized.length > MAX_SUMMARY_BYTES) {
                return "{\"summary\":\"[REDACTED:SIZE_LIMIT]\"}";
            }
            return new String(serialized, java.nio.charset.StandardCharsets.UTF_8);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("Audit summary could not be serialized.", exception);
        }
    }

    private void redactNode(JsonNode node) {
        if (node instanceof ObjectNode object) {
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitive(field.getKey())) {
                    object.put(field.getKey(), "[REDACTED]");
                } else if (field.getValue().isTextual() && field.getValue().textValue().length() > 256) {
                    object.put(field.getKey(), field.getValue().textValue().substring(0, 256) + "…");
                } else {
                    redactNode(field.getValue());
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (int index = 0; index < array.size(); index++) {
                JsonNode item = array.get(index);
                if (item.isTextual() && item.textValue().length() > 256) {
                    array.set(index, objectMapper.getNodeFactory().textNode(item.textValue().substring(0, 256) + "…"));
                    continue;
                }
                redactNode(item);
            }
        }
    }

    private static boolean isSensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains);
    }
}
