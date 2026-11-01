package in.localshop.os.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import in.localshop.os.identity.JdbcOtpChallengeRepository;
import in.localshop.os.identity.IdentityAccountService;
import in.localshop.os.identity.AuthTokenService;
import in.localshop.os.identity.AdminMfaService;
import in.localshop.os.identity.AdminMfaTotp;
import in.localshop.os.identity.MfaAuthenticationException;
import in.localshop.os.audit.AuditEventService;
import in.localshop.os.audit.AuditRedactor;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.localshop.os.identity.InvalidRefreshTokenException;
import in.localshop.os.identity.OtpChallenge;
import in.localshop.os.identity.OtpChallengeService;
import in.localshop.os.identity.OtpChallengeStatus;
import in.localshop.os.identity.OtpCodeHasher;
import in.localshop.os.identity.OtpPurpose;
import in.localshop.os.identity.OtpRateLimitException;
import in.localshop.os.identity.OtpRateLimitService;
import in.localshop.os.identity.OtpVerificationResult;
import in.localshop.os.identity.PermissionAuthorization;
import in.localshop.os.identity.SmsProvider;
import in.localshop.os.identity.TokenResponse;

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
        migrationJdbc.execute("TRUNCATE TABLE audit_logs, admin_mfa_events, admin_mfa_recovery_codes, admin_mfa_credentials, "
                + "refresh_tokens, auth_sessions, otp_rate_limits, otp_challenges, stores, tenant_members, "
                + "tenants, user_roles, users");

        insertUser(USER_A, "+15550000001");
        insertUser(USER_B, "+15550000002");
        insertUser(USER_C, "+15550000003");
        insertUser(PLATFORM_ADMIN, "+15550000004");
        migrationJdbc.update(
                "INSERT INTO user_roles (user_id, role_id, role_scope) "
                        + "SELECT ?, id, 'GLOBAL' FROM roles WHERE code = 'PLATFORM_ADMIN'",
                PLATFORM_ADMIN);

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

    @Test
    void otpPersistsOnlyPurposeBoundHmacAndAllowsOneSuccessfulUse() {
        AtomicReference<String> deliveredCode = new AtomicReference<>();
        OtpChallengeService service = otpService((phone, purpose, code) -> deliveredCode.set(code),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC")));

        UUID challengeId = service.request("+15550000006", OtpPurpose.CUSTOMER_LOGIN);
        OtpChallenge persisted = new JdbcOtpChallengeRepository(runtimeJdbc).findById(challengeId).orElseThrow();
        OtpCodeHasher hasher = hasher();

        assertThat(persisted.status()).isEqualTo(OtpChallengeStatus.SENT);
        assertThat(persisted.codeHash()).hasSize(32).isNotEqualTo(deliveredCode.get().getBytes(StandardCharsets.UTF_8));
        assertThat(hasher.matches(persisted.codeHash(), challengeId, "+15550000006", OtpPurpose.CUSTOMER_LOGIN,
                deliveredCode.get())).isTrue();
        assertThat(hasher.matches(persisted.codeHash(), challengeId, "+15550000006", OtpPurpose.SELLER_LOGIN,
                deliveredCode.get())).isFalse();
        assertThat(hasher.matches(persisted.codeHash(), UUID.randomUUID(), "+15550000006",
                OtpPurpose.CUSTOMER_LOGIN, deliveredCode.get())).isFalse();

        assertThat(service.verify(challengeId, "+15550000006", OtpPurpose.CUSTOMER_LOGIN, deliveredCode.get()))
                .isEqualTo(OtpVerificationResult.VERIFIED);
        IdentityAccountService accounts = new IdentityAccountService(
                runtimeJdbc, contextHolder, databaseContext,
                new DataSourceTransactionManager(runtimeDataSource), Clock.systemUTC());
        UUID resolvedUserId = accounts.resolveAfterVerifiedOtp("+15550000006", OtpPurpose.CUSTOMER_LOGIN).userId();
        assertThat(migrationJdbc.queryForObject("SELECT phone_verified_at IS NOT NULL FROM users WHERE id = ?",
                Boolean.class, resolvedUserId)).isTrue();
        assertThat(migrationJdbc.queryForObject(
                "SELECT count(*) FROM user_roles ur JOIN roles r ON r.id = ur.role_id "
                        + "WHERE ur.user_id = ? AND r.code = 'CUSTOMER'",
                Long.class, resolvedUserId)).isEqualTo(1);

        assertThat(service.verify(challengeId, "+15550000006", OtpPurpose.CUSTOMER_LOGIN, deliveredCode.get()))
                .isEqualTo(OtpVerificationResult.ALREADY_USED);
    }

    @Test
    void otpExpiresAndLocksAfterConfiguredInvalidAttempts() {
        AdjustableClock clock = new AdjustableClock(Instant.parse("2026-01-01T00:00:00Z"));
        OtpChallengeService service = otpService((phone, purpose, code) -> { }, clock);
        UUID expiringId = service.request("+15550000001", OtpPurpose.CUSTOMER_LOGIN);
        clock.advance(Duration.ofMinutes(6));
        assertThat(service.verify(expiringId, "+15550000001", OtpPurpose.CUSTOMER_LOGIN, "000000"))
                .isEqualTo(OtpVerificationResult.EXPIRED);

        clock.advance(Duration.ofMinutes(-6));
        UUID lockoutId = service.request("+15550000001", OtpPurpose.CUSTOMER_LOGIN);
        assertThat(service.verify(lockoutId, "+15550000001", OtpPurpose.CUSTOMER_LOGIN, "000000"))
                .isEqualTo(OtpVerificationResult.INVALID);
        assertThat(service.verify(lockoutId, "+15550000001", OtpPurpose.CUSTOMER_LOGIN, "000000"))
                .isEqualTo(OtpVerificationResult.LOCKED);
        assertThat(service.verify(lockoutId, "+15550000001", OtpPurpose.CUSTOMER_LOGIN, "000001"))
                .isEqualTo(OtpVerificationResult.LOCKED);
    }

    @Test
    void otpProviderFailureInvalidatesChallengeWithoutExposingItsCode() {
        OtpChallengeService service = otpService((phone, purpose, code) -> {
            throw new IllegalStateException("simulated provider outage");
        }, Clock.systemUTC());

        UUID challengeId = service.request("+15550000001", OtpPurpose.CUSTOMER_LOGIN);

        assertThat(new JdbcOtpChallengeRepository(runtimeJdbc).findById(challengeId).orElseThrow().status())
                .isEqualTo(OtpChallengeStatus.DELIVERY_FAILED);
        assertThat(service.verify(challengeId, "+15550000001", OtpPurpose.CUSTOMER_LOGIN, "123456"))
                .isEqualTo(OtpVerificationResult.NOT_DELIVERED);
    }

    @Test
    void otpRequestRateLimitsPhoneCooldownAndWindowWithoutStoringRawPhoneKeys() {
        OtpRateLimitService limits = rateLimitService();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        String phone = "+15550000001";
        String ip = "192.0.2.10";
        String device = "device_0123456789";

        limits.checkAndConsume(OtpRateLimitService.Operation.REQUEST, phone, ip, device, start);
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(OtpRateLimitException.class,
                () -> limits.checkAndConsume(OtpRateLimitService.Operation.REQUEST,
                        phone, ip, device, start.plusSeconds(30))).retryAfterSeconds()).isEqualTo(30);

        OtpCodeHasher hasher = hasher();
        byte[] phoneHash = hasher.hashRateLimitKey("REQUEST_PHONE_WINDOW", phone);
        assertThat(migrationJdbc.queryForObject(
                "SELECT count(*) FROM otp_rate_limits WHERE dimension = 'REQUEST_PHONE_WINDOW' "
                        + "AND subject_hash = ?",
                Long.class, phoneHash)).isEqualTo(1);
        assertThat(migrationJdbc.queryForObject(
                "SELECT count(*) FROM otp_rate_limits WHERE subject_hash = ?",
                Long.class, phone.getBytes(StandardCharsets.UTF_8))).isZero();

        for (int minute = 1; minute <= 4; minute++) {
            limits.checkAndConsume(OtpRateLimitService.Operation.REQUEST,
                    phone, ip, device, start.plus(Duration.ofMinutes(minute)));
        }
        assertThrows(OtpRateLimitException.class, () -> limits.checkAndConsume(
                OtpRateLimitService.Operation.REQUEST, phone, ip, device, start.plus(Duration.ofMinutes(5))));
        assertThat(migrationJdbc.queryForObject(
                "SELECT request_count FROM otp_rate_limits WHERE dimension = 'REQUEST_PHONE_WINDOW' "
                        + "AND subject_hash = ?",
                Integer.class, phoneHash)).isEqualTo(5);
    }

    @Test
    void otpIpLimitRejectionRollsBackOtherBucketConsumption() {
        OtpRateLimitService limits = rateLimitService();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        String ip = "192.0.2.20";
        for (int index = 1; index <= 30; index++) {
            limits.checkAndConsume(OtpRateLimitService.Operation.REQUEST,
                    "+1555" + String.format("%07d", index), ip,
                    "device_" + String.format("%010d", index), now);
        }

        String rejectedPhone = "+15550000099";
        assertThrows(OtpRateLimitException.class, () -> limits.checkAndConsume(
                OtpRateLimitService.Operation.REQUEST, rejectedPhone, ip, "device_0000000099", now));
        byte[] rejectedPhoneHash = hasher().hashRateLimitKey("REQUEST_PHONE_COOLDOWN", rejectedPhone);
        assertThat(migrationJdbc.queryForObject(
                "SELECT count(*) FROM otp_rate_limits WHERE dimension = 'REQUEST_PHONE_COOLDOWN' "
                        + "AND subject_hash = ?",
                Long.class, rejectedPhoneHash)).isZero();
    }

    @Test
    void accessJwtHasMinimalClaimsAndRefreshReplayRevokesTheWholeSessionFamily() {
        AuthTokenService tokenService = authTokenService(Clock.systemUTC(), Duration.ofDays(30));
        TokenResponse issued = tokenService.issue(USER_A, "device_0123456789");
        Jwt decoded = jwtDecoder().decode(issued.accessToken());
        UUID sessionId = UUID.fromString(issued.refreshToken().substring(0, issued.refreshToken().indexOf('.')));
        byte[] refreshHash = sha256(issued.refreshToken());

        assertThat(decoded.getSubject()).isEqualTo(USER_A.toString());
        assertThat(decoded.getIssuer().toString()).isEqualTo("https://test.local");
        assertThat(decoded.getAudience()).containsExactly("local-shop-os-api");
        assertThat(decoded.getClaims().keySet()).containsExactlyInAnyOrder("iss", "aud", "sub", "iat", "exp", "jti");
        assertThat(migrationJdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?",
                Long.class, refreshHash)).isEqualTo(1);
        assertThat(migrationJdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?",
                Long.class, issued.refreshToken().getBytes(StandardCharsets.UTF_8))).isZero();

        TokenResponse rotated = tokenService.refresh(issued.refreshToken());
        assertThat(rotated.refreshToken()).isNotEqualTo(issued.refreshToken());
        assertThat(migrationJdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE session_id = ? AND consumed_at IS NOT NULL "
                        + "AND replaced_by_token_id IS NOT NULL",
                Long.class, sessionId)).isEqualTo(1);

        assertThrows(InvalidRefreshTokenException.class, () -> tokenService.refresh(issued.refreshToken()));
        assertThat(migrationJdbc.queryForObject(
                "SELECT revoke_reason FROM auth_sessions WHERE id = ?", String.class, sessionId))
                .isEqualTo("REFRESH_REUSE");
        assertThrows(InvalidRefreshTokenException.class, () -> tokenService.refresh(rotated.refreshToken()));
    }

    @Test
    void logoutRevokesRefreshFamilyAndExpiredRefreshTokensCannotRotate() {
        AuthTokenService tokenService = authTokenService(Clock.systemUTC(), Duration.ofDays(30));
        TokenResponse issued = tokenService.issue(USER_A, "device_0123456789");
        UUID sessionId = UUID.fromString(issued.refreshToken().substring(0, issued.refreshToken().indexOf('.')));

        tokenService.revoke(issued.refreshToken());
        tokenService.revoke(issued.refreshToken());
        assertThat(migrationJdbc.queryForObject(
                "SELECT revoke_reason FROM auth_sessions WHERE id = ?", String.class, sessionId)).isEqualTo("LOGOUT");
        assertThrows(InvalidRefreshTokenException.class, () -> tokenService.refresh(issued.refreshToken()));

        AdjustableClock clock = new AdjustableClock(Instant.now());
        AuthTokenService shortSession = authTokenService(clock, Duration.ofSeconds(1));
        TokenResponse expiring = shortSession.issue(USER_A, "device_0123456789");
        clock.advance(Duration.ofSeconds(2));
        assertThrows(InvalidRefreshTokenException.class, () -> shortSession.refresh(expiring.refreshToken()));
    }

    @Test
    void deviceRevocationRevokesAllSessionsOnlyForThatUserAndDevice() {
        AuthTokenService tokenService = authTokenService(Clock.systemUTC(), Duration.ofDays(30));
        TokenResponse firstOnDevice = tokenService.issue(USER_A, "device_0123456789");
        TokenResponse secondOnDevice = tokenService.issue(USER_A, "device_0123456789");
        TokenResponse otherDevice = tokenService.issue(USER_A, "device_9876543210");

        var jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(USER_A.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        JwtAuthenticationToken deviceAuthentication = new JwtAuthenticationToken(jwt);
        deviceAuthentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(deviceAuthentication);
        try {
            tokenService.revokeDeviceSessions("device_0123456789");
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThrows(InvalidRefreshTokenException.class, () -> tokenService.refresh(firstOnDevice.refreshToken()));
        assertThrows(InvalidRefreshTokenException.class, () -> tokenService.refresh(secondOnDevice.refreshToken()));
        assertThat(tokenService.refresh(otherDevice.refreshToken()).refreshToken())
                .isNotEqualTo(otherDevice.refreshToken());
    }

    @Test
    void permissionEvaluatorResolvesPersistedGlobalAndTenantGrantsAndDeniesStaleMembership() {
        var jwtA = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(USER_A.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        var authentication = new JwtAuthenticationToken(jwtA);
        assertThat(authentication.getPrincipal()).isInstanceOf(Jwt.class);
        authentication.setAuthenticated(true);
        PermissionAuthorization authorization = new PermissionAuthorization(
                runtimeJdbc, contextHolder, databaseContext, new DataSourceTransactionManager(runtimeDataSource));

        assertThat(inContext(context(USER_A, null, TenantActorScope.CUSTOMER), () -> runtimeJdbc.queryForObject(
                "SELECT count(*) FROM user_roles ur JOIN role_permissions rp ON rp.role_id = ur.role_id "
                        + "JOIN permissions p ON p.id = rp.permission_id "
                        + "WHERE ur.user_id = ? AND p.code = 'customer.devices.manage'",
                Long.class, USER_A))).isEqualTo(1L);
        assertThat(authorization.hasGlobal(authentication, "customer.devices.manage")).isTrue();
        assertThat(authorization.hasGlobal(authentication, "platform.config.manage")).isFalse();
        assertThat(authorization.hasTenant(authentication, TENANT_A, "tenant.subscriptions.manage")).isTrue();
        assertThat(authorization.hasTenant(authentication, TENANT_B, "tenant.store.manage")).isFalse();

        migrationJdbc.update(
                "UPDATE tenant_members SET status = 'SUSPENDED' WHERE tenant_id = ? AND user_id = ?",
                TENANT_A, USER_A);
        assertThat(authorization.hasTenant(authentication, TENANT_A, "tenant.store.read")).isFalse();

        var adminJwt = Jwt.withTokenValue("mfa-step-up")
                .header("alg", "none")
                .subject(PLATFORM_ADMIN.toString())
                .claim("amr", List.of("sms", "totp"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        var adminAuthentication = new JwtAuthenticationToken(adminJwt);
        adminAuthentication.setAuthenticated(true);
        assertThat(authorization.hasAdminPermission(adminAuthentication, "platform.config.manage")).isTrue();

        var adminWithoutMfaJwt = Jwt.withTokenValue("plain-admin")
                .header("alg", "none")
                .subject(PLATFORM_ADMIN.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        var adminWithoutMfa = new JwtAuthenticationToken(adminWithoutMfaJwt);
        adminWithoutMfa.setAuthenticated(true);
        assertThat(authorization.hasAdminPermission(adminWithoutMfa, "platform.config.manage")).isFalse();
    }

    @Test
    void adminMfaEnrollmentTotpReplayRecoveryAndStepUpTokenAreEnforced() {
        AdjustableClock clock = new AdjustableClock(Instant.now());
        AuthTokenService tokenService = authTokenService(clock, Duration.ofDays(30));
        byte[] encryptionKey = new byte[32];
        java.util.Arrays.fill(encryptionKey, (byte) 0x5a);
        ObjectMapper objectMapper = new ObjectMapper();
        AuditEventService auditEvents = new AuditEventService(
                runtimeJdbc, contextHolder, databaseContext, new AuditRedactor(objectMapper),
                new DataSourceTransactionManager(runtimeDataSource), clock);
        AdminMfaService mfaService = new AdminMfaService(
                runtimeJdbc, contextHolder, databaseContext, new DataSourceTransactionManager(runtimeDataSource),
                tokenService, auditEvents, new SecureRandom(), clock,
                Base64.getEncoder().encodeToString(encryptionKey));
        Jwt adminJwt = Jwt.withTokenValue("admin-login")
                .header("alg", "none")
                .subject(PLATFORM_ADMIN.toString())
                .issuedAt(clock.instant())
                .expiresAt(clock.instant().plusSeconds(600))
                .build();
        JwtAuthenticationToken adminAuthentication = new JwtAuthenticationToken(adminJwt, List.of());
        adminAuthentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(adminAuthentication);
        try {
            var enrollment = mfaService.startEnrollment(PLATFORM_ADMIN);
            byte[] seed = AdminMfaTotp.decodeSecret(enrollment.secret());
            String enrollmentCode = AdminMfaTotp.code(seed, clock.instant());
            var completed = mfaService.completeEnrollment(PLATFORM_ADMIN, enrollmentCode);
            assertThat(completed.recoveryCodes()).hasSize(10);
            assertThat(migrationJdbc.queryForObject(
                    "SELECT secret_ciphertext FROM admin_mfa_credentials WHERE user_id = ?", byte[].class, PLATFORM_ADMIN))
                    .isNotEqualTo(seed);
            assertThat(migrationJdbc.queryForObject(
                    "SELECT count(*) FROM admin_mfa_recovery_codes WHERE user_id = ? AND used_at IS NULL",
                    Long.class, PLATFORM_ADMIN)).isEqualTo(10L);
            assertThat(jwtDecoder().decode(completed.stepUpSession().accessToken()).getClaimAsStringList("amr"))
                    .contains("totp");
            assertThrows(MfaAuthenticationException.class,
                    () -> mfaService.verify(PLATFORM_ADMIN, enrollmentCode));

            clock.advance(Duration.ofSeconds(30));
            String nextCode = AdminMfaTotp.code(seed, clock.instant());
            var verified = mfaService.verify(PLATFORM_ADMIN, nextCode);
            assertThat(jwtDecoder().decode(verified.accessToken()).getClaimAsStringList("amr"))
                    .containsExactly("sms", "totp");
            String recoveryCode = completed.recoveryCodes().getFirst();
            assertThat(mfaService.recover(PLATFORM_ADMIN, recoveryCode).accessToken()).isNotBlank();
            assertThrows(MfaAuthenticationException.class,
                    () -> mfaService.recover(PLATFORM_ADMIN, recoveryCode));
            assertThat(migrationJdbc.queryForObject(
                    "SELECT count(*) FROM admin_mfa_events WHERE user_id = ? AND event_type = 'RECOVERY_CODE_USED'",
                    Long.class, PLATFORM_ADMIN)).isEqualTo(1L);
            assertThat(migrationJdbc.queryForObject(
                    "SELECT count(*) FROM audit_logs WHERE actor_user_id = ? AND action_code LIKE 'admin.mfa.%'",
                    Long.class, PLATFORM_ADMIN)).isEqualTo(6L);
            assertThrows(DataAccessException.class,
                    () -> runtimeJdbc.update("DELETE FROM audit_logs WHERE actor_user_id = ?", PLATFORM_ADMIN));
            for (int attempt = 0; attempt < 4; attempt++) {
                assertThrows(MfaAuthenticationException.class,
                        () -> mfaService.verify(PLATFORM_ADMIN, "not-a-code"));
            }
            assertThrows(MfaAuthenticationException.class,
                    () -> mfaService.verify(PLATFORM_ADMIN, AdminMfaTotp.code(seed, clock.instant())));
            clock.advance(Duration.ofMinutes(15));
            assertThat(mfaService.verify(PLATFORM_ADMIN, AdminMfaTotp.code(seed, clock.instant()))
                    .accessToken()).isNotBlank();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private OtpRateLimitService rateLimitService() {
        return new OtpRateLimitService(runtimeJdbc, hasher(), new DataSourceTransactionManager(runtimeDataSource));
    }

    private AuthTokenService authTokenService(Clock clock, Duration refreshTtl) {
        byte[] rawKey = "test-jwt-signing-secret-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8);
        SecretKey key = new SecretKeySpec(rawKey, "HmacSHA256");
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(key)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.HS256)
                .build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        return new AuthTokenService(runtimeJdbc, encoder, new SecureRandom(), clock,
                contextHolder, databaseContext, new DataSourceTransactionManager(runtimeDataSource),
                Duration.ofMinutes(10), refreshTtl, "https://test.local", "local-shop-os-api");
    }

    private static JwtDecoder jwtDecoder() {
        byte[] rawKey = "test-jwt-signing-secret-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8);
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(rawKey, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private OtpChallengeService otpService(SmsProvider provider, Clock clock) {
        return new OtpChallengeService(new JdbcOtpChallengeRepository(runtimeJdbc), provider, hasher(),
                new SecureRandom(), clock, Duration.ofMinutes(5), 2);
    }

    private static OtpCodeHasher hasher() {
        return new OtpCodeHasher("test-only-secret-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));
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
        migrationJdbc.update(
                "INSERT INTO user_roles (user_id, role_id, role_scope) "
                        + "SELECT ?, id, 'GLOBAL' FROM roles WHERE code = 'CUSTOMER'",
                id);
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

    private static final class AdjustableClock extends Clock {
        private Instant instant;

        private AdjustableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
