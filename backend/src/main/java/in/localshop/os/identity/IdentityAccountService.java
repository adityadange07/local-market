package in.localshop.os.identity;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import in.localshop.os.tenancy.TenantActorScope;
import in.localshop.os.tenancy.TenantContextHolder;
import in.localshop.os.tenancy.TenantDatabaseContext;
import in.localshop.os.tenancy.TenantExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IdentityAccountService {

    private record UserRow(UUID id, String status) { }

    private final JdbcTemplate jdbcTemplate;
    private final TenantContextHolder contextHolder;
    private final TenantDatabaseContext databaseContext;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public IdentityAccountService(
            JdbcTemplate jdbcTemplate,
            TenantContextHolder contextHolder,
            TenantDatabaseContext databaseContext,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.contextHolder = contextHolder;
        this.databaseContext = databaseContext;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public IdentityResolution resolveAfterVerifiedOtp(String phoneE164, OtpPurpose purpose) {
        return transactionTemplate.execute(status -> {
            UserRow user = findByPhone(phoneE164, null);
            if (user == null) {
                if (purpose == OtpPurpose.SELLER_LOGIN) {
                    throw new IdentityAuthenticationRejectedException();
                }
                user = createVerifiedIdentity(phoneE164);
            }
            if (!"ACTIVE".equals(user.status())) {
                throw new IdentityAuthenticationRejectedException();
            }

            String nextAction = switch (purpose) {
                case CUSTOMER_LOGIN -> "SESSION_ISSUANCE";
                case SELLER_LOGIN -> {
                    if (!hasActiveSellerMembership(user.id())) {
                        throw new IdentityAuthenticationRejectedException();
                    }
                    yield "SESSION_ISSUANCE";
                }
                case SELLER_REGISTRATION -> hasActiveSellerMembership(user.id())
                        ? "SESSION_ISSUANCE"
                        : "SHOP_ONBOARDING";
            };
            return new IdentityResolution(user.id(), nextAction);
        });
    }

    private UserRow findByPhone(String phoneE164, UUID userId) {
        TenantExecutionContext authentication = new TenantExecutionContext(
                userId, null, TenantActorScope.IDENTITY_AUTHENTICATION);
        try (TenantContextHolder.Scope ignored = contextHolder.install(authentication)) {
            databaseContext.applyCurrent();
            List<UserRow> rows = jdbcTemplate.query(
                    "SELECT id, status FROM users WHERE phone_e164 = ?",
                    (resultSet, rowNumber) -> new UserRow(
                            resultSet.getObject("id", UUID.class), resultSet.getString("status")),
                    phoneE164);
            return rows.stream().findFirst().orElse(null);
        }
    }

    private UserRow createVerifiedIdentity(String phoneE164) {
        UUID candidateId = UUID.randomUUID();
        TenantExecutionContext provisioning = new TenantExecutionContext(
                candidateId, null, TenantActorScope.IDENTITY_PROVISIONING);
        try (TenantContextHolder.Scope ignored = contextHolder.install(provisioning)) {
            databaseContext.applyCurrent();
            List<UUID> createdIds = jdbcTemplate.query(
                    "INSERT INTO users (id, phone_e164, phone_verified_at, status) "
                            + "VALUES (?, ?, ?, 'ACTIVE') ON CONFLICT (phone_e164) DO NOTHING RETURNING id",
                    (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class),
                    candidateId, phoneE164, Timestamp.from(clock.instant()));
            if (!createdIds.isEmpty()) {
                jdbcTemplate.update(
                        "INSERT INTO user_roles (user_id, role_id, role_scope) "
                                + "SELECT ?, id, 'GLOBAL' FROM roles WHERE code = 'CUSTOMER'",
                        candidateId);
                return new UserRow(candidateId, "ACTIVE");
            }
        }
        // A concurrent verified request may have created the same phone between lookup and insert.
        UserRow existing = findByPhone(phoneE164, null);
        if (existing == null) {
            throw new IdentityAuthenticationRejectedException();
        }
        return existing;
    }

    private boolean hasActiveSellerMembership(UUID userId) {
        TenantExecutionContext authentication = new TenantExecutionContext(
                userId, null, TenantActorScope.IDENTITY_AUTHENTICATION);
        try (TenantContextHolder.Scope ignored = contextHolder.install(authentication)) {
            databaseContext.applyCurrent();
            Long count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM tenant_members WHERE user_id = ? AND status = 'ACTIVE'",
                    Long.class, userId);
            return count != null && count > 0;
        }
    }
}
