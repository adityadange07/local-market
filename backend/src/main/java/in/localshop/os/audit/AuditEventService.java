package in.localshop.os.audit;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import in.localshop.os.common.web.TraceIdFilter;
import in.localshop.os.tenancy.TenantContextHolder;
import in.localshop.os.tenancy.TenantDatabaseContext;
import in.localshop.os.tenancy.TenantExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditEventService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantContextHolder contextHolder;
    private final TenantDatabaseContext databaseContext;
    private final AuditRedactor redactor;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AuditEventService(
            JdbcTemplate jdbcTemplate,
            TenantContextHolder contextHolder,
            TenantDatabaseContext databaseContext,
            AuditRedactor redactor,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.contextHolder = contextHolder;
        this.databaseContext = databaseContext;
        this.redactor = redactor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setReadOnly(true);
        this.clock = clock;
    }

    /** Must be called inside the mutation transaction so failure rolls back both records. */
    public UUID recordCurrent(
            String actionCode,
            String objectType,
            String objectId,
            Map<String, ?> changeSummary) {
        TenantExecutionContext context = contextHolder.requireCurrent();
        UUID actorUserId = authenticatedUserId(SecurityContextHolder.getContext().getAuthentication());
        if (actorUserId == null || !actorUserId.equals(context.userId())) {
            throw new org.springframework.security.access.AccessDeniedException("Audit actor must match authenticated user.");
        }
        databaseContext.applyCurrent();
        UUID eventId = UUID.randomUUID();
        UUID correlationId = requestCorrelationId();
        String sanitized = redactor.redact(changeSummary);
        jdbcTemplate.update(
                "INSERT INTO audit_logs (id, actor_user_id, tenant_id, actor_scope, action_code, object_type, "
                        + "object_id, correlation_id, change_summary, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                eventId, actorUserId, context.tenantId(), context.actorScope().databaseValue(),
                actionCode, objectType, objectId, correlationId, sanitized, Timestamp.from(clock.instant()));
        return eventId;
    }

    @PreAuthorize("@permissionAuthorization.hasAdminPermission(authentication, 'platform.audit.read')")
    public List<AuditEvent> readRecent(Instant beforeCreatedAt, UUID beforeId, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Audit page size must be between 1 and 100.");
        }
        if ((beforeCreatedAt == null) != (beforeId == null)) {
            throw new IllegalArgumentException("Audit cursor timestamp and ID must be supplied together.");
        }
        UUID actorUserId = authenticatedUserId(SecurityContextHolder.getContext().getAuthentication());
        if (actorUserId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authenticated administrator required.");
        }
        return transactionTemplate.execute(status -> {
            TenantExecutionContext context = new TenantExecutionContext(actorUserId, null,
                    in.localshop.os.tenancy.TenantActorScope.PLATFORM_ADMIN);
            try (TenantContextHolder.Scope ignored = contextHolder.install(context)) {
                databaseContext.applyCurrent();
                String sql = "SELECT id, actor_user_id, tenant_id, actor_scope, action_code, object_type, object_id, "
                        + "correlation_id, change_summary::text AS change_summary, created_at FROM audit_logs "
                        + "WHERE (?::TIMESTAMPTZ IS NULL OR (created_at, id) < (?::TIMESTAMPTZ, ?::UUID)) "
                        + "ORDER BY created_at DESC, id DESC LIMIT ?";
                Timestamp beforeTimestamp = beforeCreatedAt == null ? null : Timestamp.from(beforeCreatedAt);
                return jdbcTemplate.query(sql, (rs, row) -> new AuditEvent(
                        rs.getObject("id", UUID.class), rs.getObject("actor_user_id", UUID.class),
                        rs.getObject("tenant_id", UUID.class), rs.getString("actor_scope"),
                        rs.getString("action_code"), rs.getString("object_type"), rs.getString("object_id"),
                        rs.getObject("correlation_id", UUID.class), rs.getString("change_summary"),
                        rs.getTimestamp("created_at").toInstant()),
                        beforeTimestamp, beforeTimestamp, beforeId, limit);
            }
        });
    }

    private static UUID authenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static UUID requestCorrelationId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        Object value = attributes.getRequest().getAttribute(TraceIdFilter.REQUEST_ATTRIBUTE);
        if (!(value instanceof String traceId)) {
            return null;
        }
        try {
            return UUID.fromString(traceId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
