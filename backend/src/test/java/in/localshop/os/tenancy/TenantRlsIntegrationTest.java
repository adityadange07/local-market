package in.localshop.os.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class TenantRlsIntegrationTest {

    private static final String APP_USERNAME = "localshop_app";
    private static final String APP_PASSWORD = "localshop_app_dev_password";

    private static final UUID USER_A = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID USER_B = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID USER_C = UUID.fromString("10000000-0000-4000-8000-000000000003");
    private static final UUID PLATFORM_ADMIN = UUID.fromString("10000000-0000-4000-8000-000000000004");
    private static final UUID USER_NEW = UUID.fromString("10000000-0000-4000-8000-000000000005");

    private static final UUID TENANT_A = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID TENANT_C = UUID.fromString("20000000-0000-4000-8000-000000000003");
    private static final UUID TENANT_NEW = UUID.fromString("20000000-0000-4000-8000-000000000004");

    private static final UUID STORE_A = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final UUID STORE_B = UUID.fromString("30000000-0000-4000-8000-000000000002");
    private static final UUID STORE_C = UUID.fromString("30000000-0000-4000-8000-000000000003");
    private static final UUID STORE_NEW = UUID.fromString("30000000-0000-4000-8000-000000000004");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.5-alpine").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("localshop")
            .withUsername("localshop")
            .withPassword("migration_test_password")
            .withInitScript("db/tenant-rls-init.sql");

    private static HikariDataSource migrationDataSource;
    private static HikariDataSource runtimeDataSource;
    private static JdbcTemplate migrationJdbc;
    private static JdbcTemplate runtimeJdbc;
    private static TenantContextHolder contextHolder;
    private static TenantDatabaseContext databaseContext;
    private static TransactionTemplate transactionTemplate;

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load()
                .migrate();

        migrationDataSource = dataSource(POSTGRES.getUsername(), POSTGRES.getPassword(), 2);
        runtimeDataSource = dataSource(APP_USERNAME, APP_PASSWORD, 1);
        migrationJdbc = new JdbcTemplate(migrationDataSource);
        runtimeJdbc = new JdbcTemplate(runtimeDataSource);
        contextHolder = new TenantContextHolder();
        databaseContext = new TenantDatabaseContext(runtimeJdbc, contextHolder);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(runtimeDataSource));
    }

    @AfterAll
    static void closeDataSources() {
        if (runtimeDataSource != null) {
            runtimeDataSource.close();
        }
        if (migrationDataSource != null) {
            migrationDataSource.close();
        }
    }

    @BeforeEach
    void seedTwoPrivateTenantsAndOnePublicTenant() {
        migrationJdbc.execute("TRUNCATE TABLE stores, tenant_members, tenants, user_roles, users");

        insertUser(USER_A, "+15550000001");
        insertUser(USER_B, "+15550000002");
        insertUser(USER_C, "+15550000003");
        insertUser(PLATFORM_ADMIN, "+15550000004");

        insertTenant(TENANT_A, "Shop A", "APPROVED", USER_A, PLATFORM_ADMIN);
        insertTenant(TENANT_B, "Shop B", "PENDING_APPROVAL", USER_B, null);
        insertTenant(TENANT_C, "Shop C", "APPROVED", USER_C, PLATFORM_ADMIN);

        insertMembership(TENANT_A, USER_A, "TENANT_OWNER");
        insertMembership(TENANT_B, USER_B, "TENANT_OWNER");
        insertMembership(TENANT_C, USER_C, "TENANT_MANAGER");

        insertStore(STORE_A, TENANT_A, "shop-a", "PUBLISHED", USER_A);
        insertStore(STORE_B, TENANT_B, "shop-b", "DRAFT", USER_B);
        insertStore(STORE_C, TENANT_C, "shop-c", "PUBLISHED", USER_C);
    }

    @Test
    void sellerCanReadOwnTenantAndPublicRowsButCannotReadPrivateTenantOrMutateOtherStore() {
        TenantExecutionContext sellerA = context(USER_A, TENANT_A, TenantActorScope.SELLER);

        assertThat(inContext(sellerA, () -> count("SELECT count(*) FROM tenants WHERE id = ?", TENANT_A)))
                .isEqualTo(1);
        assertThat(inContext(sellerA, () -> count("SELECT count(*) FROM tenants WHERE id = ?", TENANT_B)))
                .isZero();
        assertThat(inContext(sellerA, () -> count("SELECT count(*) FROM tenant_members WHERE tenant_id = ?", TENANT_B)))
                .isZero();
        assertThat(inContext(sellerA, () -> count("SELECT count(*) FROM stores WHERE id = ?", STORE_B)))
                .isZero();

        // Shop C is public, so its public store row is readable, but it is still not writable by tenant A.
        assertThat(inContext(sellerA, () -> count("SELECT count(*) FROM stores WHERE id = ?", STORE_C)))
                .isEqualTo(1);
        assertThat(inContext(sellerA, () -> runtimeJdbc.update(
                "UPDATE stores SET display_name = 'Tampered' WHERE id = ?", STORE_C)))
                .isZero();

        assertThat(inContext(sellerA, () -> count("SELECT count(*) FROM users WHERE id = ?", USER_B)))
                .isZero();
    }

    @Test
    void publicContextSeesOnlyApprovedPublishedStores() {
        TenantExecutionContext publicContext = context(null, null, TenantActorScope.PUBLIC);

        List<String> visibleSlugs = inContext(publicContext, () -> runtimeJdbc.queryForList(
                "SELECT slug FROM stores ORDER BY slug", String.class));

        assertThat(visibleSlugs).containsExactly("shop-a", "shop-c");
    }

    @Test
    void onboardingCanCreateTenantOwnerAndDraftStoreOnlyForItsOwnContext() {
        TenantExecutionContext onboarding = context(USER_A, TENANT_NEW, TenantActorScope.TENANT_ONBOARDING);

        inContext(onboarding, () -> {
            runtimeJdbc.update(
                    "INSERT INTO tenants (id, account_name, status, created_by) VALUES (?, ?, 'ONBOARDING', ?)",
                    TENANT_NEW, "New Shop", USER_A);
            runtimeJdbc.update(
                    "INSERT INTO tenant_members (tenant_id, user_id, role_id, status, joined_at) "
                            + "VALUES (?, ?, ?, 'ACTIVE', NOW())",
                    TENANT_NEW, USER_A, roleId("TENANT_OWNER"));
            runtimeJdbc.update(
                    "INSERT INTO stores (id, tenant_id, display_name, slug, status, created_by) "
                            + "VALUES (?, ?, 'New Shop', 'new-shop', 'DRAFT', ?)",
                    STORE_NEW, TENANT_NEW, USER_A);
            return null;
        });

        assertThat(migrationJdbc.queryForObject(
                "SELECT count(*) FROM tenant_members WHERE tenant_id = ? AND user_id = ?",
                Long.class, TENANT_NEW, USER_A)).isEqualTo(1);
        assertThat(migrationJdbc.queryForObject(
                "SELECT count(*) FROM stores WHERE tenant_id = ? AND status = 'DRAFT'",
                Long.class, TENANT_NEW)).isEqualTo(1);
    }

    @Test
    void onboardingCannotCreateTenantOrMembershipForAnotherIdentity() {
        TenantExecutionContext onboarding = context(USER_A, TENANT_NEW, TenantActorScope.TENANT_ONBOARDING);

        assertThrows(DataAccessException.class, () -> inContext(onboarding, () -> {
            runtimeJdbc.update(
                    "INSERT INTO tenants (id, account_name, status, created_by) VALUES (?, ?, 'ONBOARDING', ?)",
                    TENANT_B, "Forged Shop", USER_A);
            return null;
        }));

        inContext(onboarding, () -> {
            runtimeJdbc.update(
                    "INSERT INTO tenants (id, account_name, status, created_by) VALUES (?, ?, 'ONBOARDING', ?)",
                    TENANT_NEW, "New Shop", USER_A);
            return null;
        });

        assertThrows(DataAccessException.class, () -> inContext(onboarding, () -> {
            runtimeJdbc.update(
                    "INSERT INTO tenant_members (tenant_id, user_id, role_id, status, joined_at) "
                            + "VALUES (?, ?, ?, 'ACTIVE', NOW())",
                    TENANT_NEW, USER_B, roleId("TENANT_OWNER"));
            return null;
        }));
    }

    @Test
    void platformAdminCanReviewAcrossTenantsAndApprovalMakesPublishedStorePublic() {
        TenantExecutionContext admin = context(PLATFORM_ADMIN, null, TenantActorScope.PLATFORM_ADMIN);

        assertThat(inContext(admin, () -> count("SELECT count(*) FROM tenants", null))).isEqualTo(3);
        inContext(admin, () -> {
            runtimeJdbc.update(
                    "UPDATE tenants SET status = 'APPROVED', reviewed_at = NOW(), reviewed_by = ? "
                            + "WHERE id = ?",
                    PLATFORM_ADMIN, TENANT_B);
            runtimeJdbc.update("UPDATE stores SET status = 'PUBLISHED' WHERE id = ?", STORE_B);
            return null;
        });

        assertThat(inContext(context(null, null, TenantActorScope.PUBLIC),
                () -> count("SELECT count(*) FROM stores WHERE id = ?", STORE_B))).isEqualTo(1);
    }

    @Test
    void identityProvisioningCanGrantCustomerRoleButCannotSelfGrantPlatformAdmin() {
        TenantExecutionContext provisioning = context(USER_NEW, null, TenantActorScope.IDENTITY_PROVISIONING);

        inContext(provisioning, () -> {
            runtimeJdbc.update(
                    "INSERT INTO users (id, phone_e164, phone_verified_at, status) "
                            + "VALUES (?, '+15550000005', NOW(), 'ACTIVE')",
                    USER_NEW);
            runtimeJdbc.update(
                    "INSERT INTO user_roles (user_id, role_id, role_scope) "
                            + "VALUES (?, ?, 'GLOBAL')",
                    USER_NEW, roleId("CUSTOMER"));
            return null;
        });

        assertThat(inContext(context(USER_NEW, null, TenantActorScope.CUSTOMER),
                () -> count("SELECT count(*) FROM users WHERE id = ?", USER_NEW))).isEqualTo(1);
        assertThat(inContext(context(USER_NEW, null, TenantActorScope.CUSTOMER),
                () -> count("SELECT count(*) FROM users WHERE id = ?", USER_A))).isZero();

        assertThrows(DataAccessException.class, () -> inContext(provisioning, () -> {
            runtimeJdbc.update(
                    "INSERT INTO user_roles (user_id, role_id, role_scope) VALUES (?, ?, 'GLOBAL')",
                    USER_NEW, roleId("PLATFORM_ADMIN"));
            return null;
        }));
    }

    @Test
    void transactionLocalContextDoesNotLeakAcrossPooledConnections() {
        TenantExecutionContext sellerA = context(USER_A, TENANT_A, TenantActorScope.SELLER);

        assertThat(inContext(sellerA, () -> count("SELECT count(*) FROM tenant_members", null)))
                .isEqualTo(1);
        assertThat(contextHolder.current()).isEmpty();

        // The runtime pool is size one, so this query reuses the same connection after the transaction ends.
        assertThat(runtimeJdbc.queryForObject("SELECT count(*) FROM tenant_members", Long.class)).isZero();
    }

    @Test
    void runtimeLoginIsNotSuperuserBypassRoleOrProtectedTableOwner() {
        Boolean bypassesRls = runtimeJdbc.queryForObject(
                "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user",
                Boolean.class);
        String owner = runtimeJdbc.queryForObject(
                "SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid = 'tenants'::regclass",
                String.class);

        assertThat(bypassesRls).isFalse();
        assertThat(owner).isEqualTo("localshop");
    }

    private <T> T inContext(TenantExecutionContext context, Supplier<T> operation) {
        try (TenantContextHolder.Scope ignored = contextHolder.install(context)) {
            return transactionTemplate.execute(status -> {
                databaseContext.applyCurrent();
                return operation.get();
            });
        }
    }

    private Long count(String sql, UUID id) {
        if (id == null) {
            return runtimeJdbc.queryForObject(sql, Long.class);
        }
        return runtimeJdbc.queryForObject(sql, Long.class, id);
    }

    private UUID roleId(String code) {
        return migrationJdbc.queryForObject("SELECT id FROM roles WHERE code = ?", UUID.class, code);
    }

    private void insertUser(UUID id, String phone) {
        migrationJdbc.update(
                "INSERT INTO users (id, phone_e164, phone_verified_at, status) VALUES (?, ?, NOW(), 'ACTIVE')",
                id, phone);
    }

    private void insertTenant(UUID id, String name, String status, UUID createdBy, UUID reviewer) {
        if (reviewer == null) {
            migrationJdbc.update(
                    "INSERT INTO tenants (id, account_name, status, created_by) VALUES (?, ?, ?, ?)",
                    id, name, status, createdBy);
            return;
        }
        migrationJdbc.update(
                "INSERT INTO tenants (id, account_name, status, reviewed_at, reviewed_by, created_by) "
                        + "VALUES (?, ?, ?, NOW(), ?, ?)",
                id, name, status, reviewer, createdBy);
    }

    private void insertMembership(UUID tenantId, UUID userId, String roleCode) {
        migrationJdbc.update(
                "INSERT INTO tenant_members (tenant_id, user_id, role_id, status, joined_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', NOW())",
                tenantId, userId, roleId(roleCode));
    }

    private void insertStore(UUID id, UUID tenantId, String slug, String status, UUID createdBy) {
        migrationJdbc.update(
                "INSERT INTO stores (id, tenant_id, display_name, slug, status, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, tenantId, slug, slug, status, createdBy);
    }

    private static TenantExecutionContext context(UUID userId, UUID tenantId, TenantActorScope scope) {
        return new TenantExecutionContext(userId, tenantId, scope);
    }

    private static HikariDataSource dataSource(String username, String password, int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(poolSize);
        config.setPoolName("tenant-rls-test-" + username);
        return new HikariDataSource(config);
    }
}
