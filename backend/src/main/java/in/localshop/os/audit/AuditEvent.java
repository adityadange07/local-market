package in.localshop.os.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
        UUID id,
        UUID actorUserId,
        UUID tenantId,
        String actorScope,
        String actionCode,
        String objectType,
        String objectId,
        UUID correlationId,
        String changeSummary,
        Instant createdAt) { }
