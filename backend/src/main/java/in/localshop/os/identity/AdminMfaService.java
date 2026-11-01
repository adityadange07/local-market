package in.localshop.os.identity;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import in.localshop.os.tenancy.TenantActorScope;
import in.localshop.os.tenancy.TenantContextHolder;
import in.localshop.os.tenancy.TenantDatabaseContext;
import in.localshop.os.tenancy.TenantExecutionContext;
import org.springframework.beans.factory.annotation.Value;
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
public class AdminMfaService {

    private record Credential(byte[] encryptedSecret, Instant pendingExpiresAt,
            Instant enabledAt, Long lastVerifiedStep) { }
    private record EnrollmentResult(List<String> recoveryCodes) { }

    private static final int RECOVERY_CODE_COUNT = 10;
    private static final Duration ENROLLMENT_TTL = Duration.ofMinutes(10);
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCKOUT = Duration.ofMinutes(15);
    private static final int GCM_NONCE_BYTES = 12;

    private final JdbcTemplate jdbcTemplate;
    private final TenantContextHolder contextHolder;
    private final TenantDatabaseContext databaseContext;
    private final TransactionTemplate transactionTemplate;
    private final AuthTokenService tokenService;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final byte[] encryptionKey;

    public AdminMfaService(
            JdbcTemplate jdbcTemplate,
            TenantContextHolder contextHolder,
            TenantDatabaseContext databaseContext,
            PlatformTransactionManager transactionManager,
            AuthTokenService tokenService,
            SecureRandom secureRandom,
            Clock clock,
            @Value("${localshop.security.mfa.encryption-key}") String encodedEncryptionKey) {
        this.jdbcTemplate = jdbcTemplate;
        this.contextHolder = contextHolder;
        this.databaseContext = databaseContext;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.tokenService = tokenService;
        this.secureRandom = secureRandom;
        this.clock = clock;
        try {
            this.encryptionKey = Base64.getDecoder().decode(encodedEncryptionKey);
            if (this.encryptionKey.length != 32) {
                throw new IllegalArgumentException("MFA encryption key must be 32 bytes.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("MFA_ENCRYPTION_KEY must be Base64 encoding of 32 random bytes.", exception);
        }
    }

    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'platform.config.manage')")
    public AdminMfaEnrollmentStartResponse startEnrollment(UUID userId) {
        requireCaller(userId);
        byte[] secret = new byte[20];
        secureRandom.nextBytes(secret);
        String encodedSecret = AdminMfaTotp.encodeSecret(secret);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ENROLLMENT_TTL);
        byte[] encryptedSecret = encrypt(secret);
        transactionTemplate.executeWithoutResult(status -> inAdminContext(userId, () -> {
            Credential current = findCredential(userId, true);
            if (current != null && current.enabledAt() != null) {
                throw new MfaAlreadyEnrolledException();
            }
            jdbcTemplate.update(
                    "INSERT INTO admin_mfa_credentials (user_id, secret_ciphertext, pending_expires_at, "
                            + "enabled_at, last_verified_step, created_at, updated_at) VALUES (?, ?, ?, NULL, NULL, ?, ?) "
                            + "ON CONFLICT (user_id) DO UPDATE SET secret_ciphertext = EXCLUDED.secret_ciphertext, "
                            + "pending_expires_at = EXCLUDED.pending_expires_at, last_verified_step = NULL, "
                            + "updated_at = EXCLUDED.updated_at WHERE admin_mfa_credentials.enabled_at IS NULL",
                    userId, encryptedSecret, Timestamp.from(expiresAt), Timestamp.from(now), Timestamp.from(now));
            recordEvent(userId, "ENROLLMENT_STARTED", now);
        }));
        String label = "Local Shop OS:" + userId;
        String uri = "otpauth://totp/" + java.net.URLEncoder.encode(label, java.nio.charset.StandardCharsets.UTF_8)
                + "?secret=" + encodedSecret + "&issuer=Local%20Shop%20OS&algorithm=SHA1&digits=6&period=30";
        return new AdminMfaEnrollmentStartResponse(encodedSecret, uri, expiresAt);
    }

    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'platform.config.manage')")
    public AdminMfaEnrollmentCompleteResponse completeEnrollment(UUID userId, String code) {
        requireCaller(userId);
        Instant now = clock.instant();
        EnrollmentResult result = transactionTemplate.execute(status -> inAdminContext(userId, () -> {
            Credential credential = findCredential(userId, true);
            if (credential == null || credential.enabledAt() != null || credential.pendingExpiresAt() == null
                    || !credential.pendingExpiresAt().isAfter(now)) {
                return null;
            }
            if (isLocked(userId, now)) {
                return null;
            }
            byte[] secret = decrypt(credential.encryptedSecret());
            OptionalLong matchingStep = AdminMfaTotp.matchingStep(secret, code, now, credential.lastVerifiedStep());
            if (matchingStep.isEmpty()) {
                recordFailedAttempt(userId, now);
                recordEvent(userId, "VERIFICATION_FAILED", now);
                return null;
            }
            jdbcTemplate.update(
                    "UPDATE admin_mfa_credentials SET enabled_at = ?, pending_expires_at = NULL, "
                            + "last_verified_step = ?, failed_attempts = 0, attempt_window_started_at = NULL, "
                            + "locked_until = NULL, updated_at = ? WHERE user_id = ? AND enabled_at IS NULL",
                    Timestamp.from(now), matchingStep.getAsLong(), Timestamp.from(now), userId);
            List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
            for (int index = 0; index < RECOVERY_CODE_COUNT; index++) {
                String recoveryCode = newRecoveryCode();
                codes.add(recoveryCode);
                jdbcTemplate.update(
                        "INSERT INTO admin_mfa_recovery_codes (user_id, code_hash, created_at) VALUES (?, ?, ?)",
                        userId, hashRecoveryCode(recoveryCode), Timestamp.from(now));
            }
            recordEvent(userId, "ENROLLMENT_COMPLETED", now);
            return new EnrollmentResult(List.copyOf(codes));
        }));
        if (result == null) {
            throw new MfaAuthenticationException();
        }
        return new AdminMfaEnrollmentCompleteResponse(tokenService.issueMfaStepUpToken(userId), result.recoveryCodes());
    }

    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'platform.config.manage')")
    public AdminMfaTokenResponse verify(UUID userId, String code) {
        requireCaller(userId);
        Instant now = clock.instant();
        Boolean valid = transactionTemplate.execute(status -> inAdminContext(userId, () -> {
            Credential credential = findCredential(userId, true);
            if (credential == null || credential.enabledAt() == null) {
                return false;
            }
            if (isLocked(userId, now)) {
                return false;
            }
            byte[] secret = decrypt(credential.encryptedSecret());
            OptionalLong matchingStep = AdminMfaTotp.matchingStep(secret, code, now, credential.lastVerifiedStep());
            if (matchingStep.isEmpty()) {
                recordFailedAttempt(userId, now);
                recordEvent(userId, "VERIFICATION_FAILED", now);
                return false;
            }
            jdbcTemplate.update(
                    "UPDATE admin_mfa_credentials SET last_verified_step = ?, failed_attempts = 0, "
                            + "attempt_window_started_at = NULL, locked_until = NULL, updated_at = ? WHERE user_id = ?",
                    matchingStep.getAsLong(), Timestamp.from(now), userId);
            recordEvent(userId, "TOTP_VERIFIED", now);
            return true;
        }));
        if (!Boolean.TRUE.equals(valid)) {
            throw new MfaAuthenticationException();
        }
        return tokenService.issueMfaStepUpToken(userId);
    }

    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'platform.config.manage')")
    public AdminMfaTokenResponse recover(UUID userId, String recoveryCode) {
        requireCaller(userId);
        Instant now = clock.instant();
        Integer consumed = transactionTemplate.execute(status -> inAdminContext(userId, () -> {
            Credential credential = findCredential(userId, false);
            if (credential == null || credential.enabledAt() == null) {
                return 0;
            }
            if (isLocked(userId, now)) {
                return 0;
            }
            int updated = jdbcTemplate.update(
                    "UPDATE admin_mfa_recovery_codes SET used_at = ? WHERE user_id = ? AND code_hash = ? "
                            + "AND used_at IS NULL",
                    Timestamp.from(now), userId, hashRecoveryCode(recoveryCode));
            if (updated == 1) {
                jdbcTemplate.update(
                        "UPDATE admin_mfa_credentials SET failed_attempts = 0, attempt_window_started_at = NULL, "
                                + "locked_until = NULL, updated_at = ? WHERE user_id = ?",
                        Timestamp.from(now), userId);
                recordEvent(userId, "RECOVERY_CODE_USED", now);
            } else {
                recordFailedAttempt(userId, now);
                recordEvent(userId, "VERIFICATION_FAILED", now);
            }
            return updated;
        }));
        if (consumed == null || consumed != 1) {
            throw new MfaAuthenticationException();
        }
        return tokenService.issueMfaStepUpToken(userId);
    }

    private Credential findCredential(UUID userId, boolean lock) {
        String sql = "SELECT secret_ciphertext, pending_expires_at, enabled_at, last_verified_step "
                + "FROM admin_mfa_credentials WHERE user_id = ?" + (lock ? " FOR UPDATE" : "");
        List<Credential> rows = jdbcTemplate.query(sql, (rs, row) -> {
            Object step = rs.getObject("last_verified_step");
            return new Credential(rs.getBytes("secret_ciphertext"), instant(rs.getTimestamp("pending_expires_at")),
                    instant(rs.getTimestamp("enabled_at")), step == null ? null : ((Number) step).longValue());
        }, userId);
        return rows.stream().findFirst().orElse(null);
    }

    private static void requireCaller(UUID userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Authenticated administrator required.");
        }
        try {
            if (!userId.equals(UUID.fromString(jwt.getSubject()))) {
                throw new AccessDeniedException("Authenticated administrator required.");
            }
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Authenticated administrator required.");
        }
    }

    private void recordEvent(UUID userId, String eventType, Instant at) {
        jdbcTemplate.update("INSERT INTO admin_mfa_events (id, user_id, event_type, created_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), userId, eventType, Timestamp.from(at));
    }

    private boolean isLocked(UUID userId, Instant now) {
        Boolean locked = jdbcTemplate.queryForObject(
                "SELECT locked_until IS NOT NULL AND locked_until > ? FROM admin_mfa_credentials WHERE user_id = ?",
                Boolean.class, Timestamp.from(now), userId);
        return Boolean.TRUE.equals(locked);
    }

    private void recordFailedAttempt(UUID userId, Instant now) {
        Timestamp at = Timestamp.from(now);
        Timestamp windowStart = Timestamp.from(now.minus(ATTEMPT_WINDOW));
        Timestamp lockUntil = Timestamp.from(now.plus(LOCKOUT));
        jdbcTemplate.update(
                "UPDATE admin_mfa_credentials SET "
                        + "failed_attempts = CASE WHEN attempt_window_started_at IS NULL OR attempt_window_started_at < ? "
                        + "THEN 1 ELSE LEAST(failed_attempts + 1, 5) END, "
                        + "attempt_window_started_at = CASE WHEN attempt_window_started_at IS NULL "
                        + "OR attempt_window_started_at < ? THEN ? ELSE attempt_window_started_at END, "
                        + "locked_until = CASE WHEN (CASE WHEN attempt_window_started_at IS NULL "
                        + "OR attempt_window_started_at < ? THEN 1 ELSE failed_attempts + 1 END) >= 5 "
                        + "THEN ? ELSE locked_until END, updated_at = ? WHERE user_id = ?",
                windowStart, windowStart, at, windowStart, lockUntil, at, userId);
    }

    private void inAdminContext(UUID userId, Runnable action) {
        TenantExecutionContext context = new TenantExecutionContext(userId, null, TenantActorScope.PLATFORM_ADMIN);
        try (TenantContextHolder.Scope ignored = contextHolder.install(context)) {
            databaseContext.applyCurrent();
            action.run();
        }
    }

    private <T> T inAdminContext(UUID userId, java.util.function.Supplier<T> action) {
        TenantExecutionContext context = new TenantExecutionContext(userId, null, TenantActorScope.PLATFORM_ADMIN);
        try (TenantContextHolder.Scope ignored = contextHolder.install(context)) {
            databaseContext.applyCurrent();
            return action.get();
        }
    }

    private byte[] encrypt(byte[] plaintext) {
        try {
            byte[] nonce = new byte[GCM_NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("MFA secret encryption failed.", exception);
        }
    }

    private byte[] decrypt(byte[] ciphertext) {
        try {
            ByteBuffer input = ByteBuffer.wrap(ciphertext);
            byte[] nonce = new byte[GCM_NONCE_BYTES];
            input.get(nonce);
            byte[] encrypted = new byte[input.remaining()];
            input.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
            return cipher.doFinal(encrypted);
        } catch (java.security.GeneralSecurityException | java.nio.BufferUnderflowException exception) {
            throw new IllegalStateException("Stored MFA secret could not be decrypted.", exception);
        }
    }

    private String newRecoveryCode() {
        byte[] raw = new byte[12];
        secureRandom.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String hashRecoveryCode(String code) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(code.trim().toUpperCase(java.util.Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
