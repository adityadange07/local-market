package in.localshop.os.identity;

import java.util.List;
import java.util.UUID;

import in.localshop.os.tenancy.TenantActorScope;
import in.localshop.os.tenancy.TenantContextHolder;
import in.localshop.os.tenancy.TenantDatabaseContext;
import in.localshop.os.tenancy.TenantExecutionContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Resolves current authorization from persisted grants; JWT claims never grant roles. */
@Component("permissionAuthorization")
public class PermissionAuthorization {

    private final JdbcTemplate jdbcTemplate;
    private final TenantContextHolder contextHolder;
    private final TenantDatabaseContext databaseContext;
    private final TransactionTemplate transactionTemplate;

    public PermissionAuthorization(
            JdbcTemplate jdbcTemplate,
            TenantContextHolder contextHolder,
            TenantDatabaseContext databaseContext,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.contextHolder = contextHolder;
        this.databaseContext = databaseContext;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setReadOnly(true);
    }

    public boolean hasGlobal(Authentication authentication, String permission) {
        UUID userId = authenticatedUserId(authentication);
        if (userId == null || !validPermission(permission)) {
            return false;
        }
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            TenantExecutionContext context = new TenantExecutionContext(
                    userId, null, TenantActorScope.CUSTOMER);
            try (TenantContextHolder.Scope ignored = contextHolder.install(context)) {
                databaseContext.applyCurrent();
                List<Boolean> rows = jdbcTemplate.query(
                        "SELECT EXISTS (SELECT 1 FROM users u "
                                + "JOIN user_roles ur ON ur.user_id = u.id AND ur.role_scope = 'GLOBAL' "
                                + "JOIN role_permissions rp ON rp.role_id = ur.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE u.id = ? AND u.status = 'ACTIVE' AND p.code = ?)",
                        (resultSet, rowNumber) -> resultSet.getBoolean(1), userId, permission);
                return rows.getFirst();
            }
        }));
    }

    public boolean hasTenant(Authentication authentication, UUID tenantId, String permission) {
        UUID userId = authenticatedUserId(authentication);
        if (userId == null || tenantId == null || !validPermission(permission)) {
            return false;
        }
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            TenantExecutionContext context = new TenantExecutionContext(
                    userId, tenantId, TenantActorScope.SELLER);
            try (TenantContextHolder.Scope ignored = contextHolder.install(context)) {
                databaseContext.applyCurrent();
                List<Boolean> rows = jdbcTemplate.query(
                        "SELECT EXISTS (SELECT 1 FROM users u "
                                + "JOIN tenant_members tm ON tm.user_id = u.id AND tm.tenant_id = ? "
                                + "AND tm.status = 'ACTIVE' AND tm.role_scope = 'TENANT' "
                                + "JOIN role_permissions rp ON rp.role_id = tm.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE u.id = ? AND u.status = 'ACTIVE' AND p.code = ?)",
                        (resultSet, rowNumber) -> resultSet.getBoolean(1), tenantId, userId, permission);
                return rows.getFirst();
            }
        }));
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

    private static boolean validPermission(String permission) {
        return permission != null && permission.matches("[a-z][a-z0-9_.]{0,99}");
    }
}
