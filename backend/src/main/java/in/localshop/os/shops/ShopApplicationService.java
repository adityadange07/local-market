package in.localshop.os.shops;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import in.localshop.os.audit.AuditEventService;
import in.localshop.os.tenancy.TenantActorScope;
import in.localshop.os.tenancy.TenantContextHolder;
import in.localshop.os.tenancy.TenantDatabaseContext;
import in.localshop.os.tenancy.TenantExecutionContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class ShopApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantContextHolder contextHolder;
    private final TenantDatabaseContext databaseContext;
    private final TransactionTemplate transactionTemplate;
    private final AuditEventService auditEventService;
    private final Clock clock;

    public ShopApplicationService(
            JdbcTemplate jdbcTemplate,
            TenantContextHolder contextHolder,
            TenantDatabaseContext databaseContext,
            PlatformTransactionManager transactionManager,
            AuditEventService auditEventService,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.contextHolder = contextHolder;
        this.databaseContext = databaseContext;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.auditEventService = auditEventService;
        this.clock = clock;
    }

    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'tenant.applications.create')")
    public ShopApplicationView submit(ShopApplicationRequest request) {
        UUID userId = currentUserId();
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        Instant now = clock.instant();
        String accountName = request.accountName().trim();
        String storeName = request.storeName().trim();
        String slug = request.slug().trim();
        try {
            return transactionTemplate.execute(status -> inContext(userId, tenantId,
                    TenantActorScope.TENANT_ONBOARDING, () -> {
                        jdbcTemplate.update(
                                "INSERT INTO tenants (id, account_name, status, created_by, created_at, updated_at) "
                                        + "VALUES (?, ?, 'ONBOARDING', ?, ?, ?)",
                                tenantId, accountName, userId, Timestamp.from(now), Timestamp.from(now));
                        jdbcTemplate.update(
                                "INSERT INTO stores (id, tenant_id, display_name, slug, status, created_by, created_at, updated_at) "
                                        + "VALUES (?, ?, ?, ?, 'DRAFT', ?, ?, ?)",
                                storeId, tenantId, storeName, slug, userId, Timestamp.from(now), Timestamp.from(now));
                        jdbcTemplate.update(
                                "INSERT INTO tenant_members (tenant_id, user_id, role_id, role_scope, status, joined_at, created_at, updated_at) "
                                        + "SELECT ?, ?, id, 'TENANT', 'ACTIVE', ?, ?, ? FROM roles WHERE code = 'TENANT_OWNER'",
                                tenantId, userId, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
                        jdbcTemplate.update(
                                "UPDATE tenants SET status = 'PENDING_APPROVAL', updated_at = ? WHERE id = ?",
                                Timestamp.from(now), tenantId);
                        auditEventService.recordCurrent("shop.application.submitted", "tenant", tenantId.toString(),
                                java.util.Map.of("accountName", accountName, "storeName", storeName, "slug", slug));
                        return new ShopApplicationView(tenantId, accountName, storeName, slug,
                                "PENDING_APPROVAL", now, null, null);
                    }));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Shop URL is unavailable.");
        }
    }

    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'tenant.applications.create')")
    public ShopApplicationList ownApplications(int limit) {
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("Application page size must be between 1 and 50.");
        }
        UUID userId = currentUserId();
        return transactionTemplate.execute(status -> inContext(userId, null,
                TenantActorScope.IDENTITY_AUTHENTICATION, () -> {
                    List<ShopApplicationView> rows = jdbcTemplate.query(
                            "SELECT t.id AS tenant_id, t.account_name, s.display_name AS store_name, s.slug, "
                                    + "t.status, t.created_at, t.reviewed_at, t.rejection_reason "
                                    + "FROM tenant_members tm JOIN tenants t ON t.id = tm.tenant_id "
                                    + "JOIN stores s ON s.tenant_id = t.id JOIN roles r ON r.id = tm.role_id "
                                    + "WHERE tm.user_id = ? AND tm.status = 'ACTIVE' AND r.code = 'TENANT_OWNER' "
                                    + "ORDER BY t.created_at DESC, t.id DESC LIMIT ?",
                            (rs, row) -> new ShopApplicationView(
                                    rs.getObject("tenant_id", UUID.class), rs.getString("account_name"),
                                    rs.getString("store_name"), rs.getString("slug"), rs.getString("status"),
                                    rs.getTimestamp("created_at").toInstant(), instant(rs.getTimestamp("reviewed_at")),
                                    rs.getString("rejection_reason")), userId, limit);
                    return new ShopApplicationList(rows);
                }));
    }

    @PreAuthorize("@permissionAuthorization.hasAdminPermission(authentication, 'platform.shops.read')")
    public ShopApplicationList pendingApplications(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Application page size must be between 1 and 100.");
        }
        UUID adminId = currentUserId();
        return transactionTemplate.execute(status -> inContext(adminId, null,
                TenantActorScope.PLATFORM_ADMIN, () -> {
                    List<ShopApplicationView> rows = jdbcTemplate.query(
                            "SELECT t.id AS tenant_id, t.account_name, s.display_name AS store_name, s.slug, "
                                    + "t.status, t.created_at, t.reviewed_at, t.rejection_reason "
                                    + "FROM tenants t JOIN stores s ON s.tenant_id = t.id "
                                    + "WHERE t.status = 'PENDING_APPROVAL' ORDER BY t.created_at ASC, t.id ASC LIMIT ?",
                            (rs, row) -> new ShopApplicationView(
                                    rs.getObject("tenant_id", UUID.class), rs.getString("account_name"),
                                    rs.getString("store_name"), rs.getString("slug"), rs.getString("status"),
                                    rs.getTimestamp("created_at").toInstant(), instant(rs.getTimestamp("reviewed_at")),
                                    rs.getString("rejection_reason")), limit);
                    return new ShopApplicationList(rows);
                }));
    }

    @PreAuthorize("@permissionAuthorization.hasAdminPermission(authentication, 'platform.shops.review')")
    public ShopApplicationView approve(UUID tenantId) {
        return review(tenantId, null, true);
    }

    @PreAuthorize("@permissionAuthorization.hasAdminPermission(authentication, 'platform.shops.review')")
    public ShopApplicationView reject(UUID tenantId, String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw new IllegalArgumentException("Rejection reason must contain 10 to 500 characters.");
        }
        return review(tenantId, normalized, false);
    }

    private ShopApplicationView review(UUID tenantId, String rejectionReason, boolean approve) {
        UUID adminId = currentUserId();
        Instant now = clock.instant();
        return transactionTemplate.execute(status -> inContext(adminId, null,
                TenantActorScope.PLATFORM_ADMIN, () -> {
                    List<String> states = jdbcTemplate.query(
                            "SELECT status FROM tenants WHERE id = ? FOR UPDATE",
                            (rs, row) -> rs.getString(1), tenantId);
                    if (states.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop application not found.");
                    }
                    if (!"PENDING_APPROVAL".equals(states.getFirst())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Shop application is no longer pending review.");
                    }
                    String nextStatus = approve ? "APPROVED" : "REJECTED";
                    jdbcTemplate.update(
                            "UPDATE tenants SET status = ?, reviewed_at = ?, reviewed_by = ?, rejection_reason = ?, "
                                    + "updated_by = ?, updated_at = ? WHERE id = ?",
                            nextStatus, Timestamp.from(now), adminId, rejectionReason, adminId, Timestamp.from(now), tenantId);
                    auditEventService.recordCurrent(
                            approve ? "shop.application.approved" : "shop.application.rejected", "tenant",
                            tenantId.toString(), rejectionReason == null
                                    ? java.util.Map.of("status", nextStatus)
                                    : java.util.Map.of("status", nextStatus, "reasonProvided", true));
                    List<ShopApplicationView> applications = jdbcTemplate.query(
                            "SELECT t.id AS tenant_id, t.account_name, s.display_name AS store_name, s.slug, "
                                    + "t.status, t.created_at, t.reviewed_at, t.rejection_reason "
                                    + "FROM tenants t JOIN stores s ON s.tenant_id = t.id WHERE t.id = ?",
                            (rs, row) -> new ShopApplicationView(
                                    rs.getObject("tenant_id", UUID.class), rs.getString("account_name"),
                                    rs.getString("store_name"), rs.getString("slug"), rs.getString("status"),
                                    rs.getTimestamp("created_at").toInstant(), instant(rs.getTimestamp("reviewed_at")),
                                    rs.getString("rejection_reason")), tenantId);
                    return applications.getFirst();
                }));
    }

    private <T> T inContext(UUID userId, UUID tenantId, TenantActorScope actorScope,
            java.util.function.Supplier<T> operation) {
        TenantExecutionContext context = new TenantExecutionContext(userId, tenantId, actorScope);
        try (TenantContextHolder.Scope ignored = contextHolder.install(context)) {
            databaseContext.applyCurrent();
            return operation.get();
        }
    }

    private static UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Authenticated user required.");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Authenticated user required.");
        }
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
