package in.localshop.os.identity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import in.localshop.os.tenancy.TenantActorScope;
import in.localshop.os.tenancy.TenantContextHolder;
import in.localshop.os.tenancy.TenantDatabaseContext;
import in.localshop.os.tenancy.TenantExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthTokenService {

    private enum RotationState { ROTATED, INVALID, REPLAYED }

    private record RefreshRow(
            UUID tokenId,
            UUID userId,
            Instant tokenExpiresAt,
            Instant tokenConsumedAt,
            Instant tokenRevokedAt,
            Instant sessionExpiresAt,
            Instant sessionRevokedAt) { }

    private record RotationResult(RotationState state, TokenResponse tokens) { }

    private final JdbcTemplate jdbcTemplate;
    private final JwtEncoder jwtEncoder;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final Duration accessTokenTtl;
    private final Duration refreshSessionTtl;
    private final String issuer;
    private final String audience;
    private final TenantContextHolder contextHolder;
    private final TenantDatabaseContext databaseContext;
    private final TransactionTemplate transactionTemplate;

    public AuthTokenService(
            JdbcTemplate jdbcTemplate,
            JwtEncoder jwtEncoder,
            SecureRandom secureRandom,
            Clock clock,
            TenantContextHolder contextHolder,
            TenantDatabaseContext databaseContext,
            PlatformTransactionManager transactionManager,
            @Value("${localshop.security.jwt.access-token-ttl:10m}") Duration accessTokenTtl,
            @Value("${localshop.security.jwt.refresh-session-ttl:30d}") Duration refreshSessionTtl,
            @Value("${localshop.security.jwt.issuer}") String issuer,
            @Value("${localshop.security.jwt.audience}") String audience) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtEncoder = jwtEncoder;
        this.secureRandom = secureRandom;
        this.clock = clock;
        this.contextHolder = contextHolder;
        this.databaseContext = databaseContext;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.accessTokenTtl = positive(accessTokenTtl, "accessTokenTtl");
        this.refreshSessionTtl = positive(refreshSessionTtl, "refreshSessionTtl");
        this.issuer = issuer;
        this.audience = audience;
        if (accessTokenTtl.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException("Access JWT lifetime cannot exceed 30 minutes.");
        }
        if (refreshSessionTtl.compareTo(Duration.ofDays(90)) > 0) {
            throw new IllegalArgumentException("Refresh session lifetime cannot exceed 90 days.");
        }
    }

    public TokenResponse issue(UUID userId, String deviceId) {
        Instant now = clock.instant();
        UUID sessionId = UUID.randomUUID();
        Instant sessionExpiry = now.plus(refreshSessionTtl);
        String refreshToken = createRefreshToken(sessionId);
        byte[] refreshHash = hashToken(refreshToken);
        byte[] deviceHash = hashDeviceId(deviceId);
        UUID refreshTokenId = UUID.randomUUID();

        return transactionTemplate.execute(status -> {
            jdbcTemplate.update(
                    "INSERT INTO auth_sessions (id, user_id, device_id_hash, created_at, last_seen_at, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    sessionId, userId, deviceHash, Timestamp.from(now), Timestamp.from(now),
                    Timestamp.from(sessionExpiry));
            jdbcTemplate.update(
                    "INSERT INTO refresh_tokens (id, session_id, token_hash, created_at, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    refreshTokenId, sessionId, refreshHash, Timestamp.from(now), Timestamp.from(sessionExpiry));
            return tokenResponse(userId, refreshToken, sessionExpiry, now);
        });
    }

    public TokenResponse refresh(String rawRefreshToken) {
        ParsedRefreshToken parsed = parse(rawRefreshToken);
        if (parsed == null) {
            throw new InvalidRefreshTokenException();
        }
        Instant now = clock.instant();
        UUID nextTokenId = UUID.randomUUID();
        String nextRefreshToken = createRefreshToken(parsed.sessionId());
        byte[] nextHash = hashToken(nextRefreshToken);
        RotationResult result = transactionTemplate.execute(status -> rotate(
                parsed, nextTokenId, nextRefreshToken, nextHash, now));
        if (result == null || result.state() != RotationState.ROTATED) {
            throw new InvalidRefreshTokenException();
        }
        return result.tokens();
    }

    /** Idempotently revokes the entire session family represented by a refresh token. */
    public void revoke(String rawRefreshToken) {
        ParsedRefreshToken parsed = parse(rawRefreshToken);
        if (parsed == null) {
            return;
        }
        Instant now = clock.instant();
        transactionTemplate.executeWithoutResult(status -> {
            if (!lockSession(parsed.sessionId())) {
                return;
            }
            RefreshRow row = findForUpdate(parsed);
            if (row != null && row.sessionRevokedAt() == null) {
                revokeFamily(parsed.sessionId(), now, "LOGOUT");
            }
        });
    }

    @PreAuthorize("@permissionAuthorization.hasGlobal(authentication, 'customer.devices.manage')")
    public void revokeDeviceSessions(String deviceId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Authenticated user required.");
        }
        final UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Authenticated user required.");
        }
        byte[] deviceHash = hashDeviceId(deviceId);
        Instant now = clock.instant();
        transactionTemplate.executeWithoutResult(status -> {
            List<UUID> sessionIds = jdbcTemplate.query(
                    "SELECT id FROM auth_sessions WHERE user_id = ? AND device_id_hash = ? "
                            + "AND revoked_at IS NULL ORDER BY id FOR UPDATE",
                    (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class), userId, deviceHash);
            if (sessionIds.isEmpty()) {
                return;
            }
            jdbcTemplate.update(
                    "UPDATE auth_sessions SET revoked_at = ?, revoke_reason = 'ADMIN_REVOKE' "
                            + "WHERE user_id = ? AND device_id_hash = ? AND revoked_at IS NULL",
                    Timestamp.from(now), userId, deviceHash);
            jdbcTemplate.update(
                    "UPDATE refresh_tokens SET revoked_at = COALESCE(revoked_at, ?) WHERE session_id IN "
                            + "(SELECT id FROM auth_sessions WHERE user_id = ? AND device_id_hash = ?)",
                    Timestamp.from(now), userId, deviceHash);
        });
    }

    private RotationResult rotate(
            ParsedRefreshToken parsed,
            UUID nextTokenId,
            String nextRefreshToken,
            byte[] nextHash,
            Instant now) {
        if (!lockSession(parsed.sessionId())) {
            return new RotationResult(RotationState.INVALID, null);
        }
        RefreshRow row = findForUpdate(parsed);
        if (row == null || row.sessionRevokedAt() != null || row.tokenRevokedAt() != null
                || !row.tokenExpiresAt().isAfter(now) || !row.sessionExpiresAt().isAfter(now)) {
            return new RotationResult(RotationState.INVALID, null);
        }
        if (row.tokenConsumedAt() != null) {
            revokeFamily(parsed.sessionId(), now, "REFRESH_REUSE");
            return new RotationResult(RotationState.REPLAYED, null);
        }
        if (!isUserActive(row.userId())) {
            revokeFamily(parsed.sessionId(), now, "ACCOUNT_DISABLED");
            return new RotationResult(RotationState.INVALID, null);
        }

        TokenResponse nextTokens = tokenResponse(row.userId(), nextRefreshToken, row.sessionExpiresAt(), now);
        jdbcTemplate.update(
                "INSERT INTO refresh_tokens (id, session_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?)",
                nextTokenId, parsed.sessionId(), nextHash, Timestamp.from(now), Timestamp.from(row.sessionExpiresAt()));
        int consumed = jdbcTemplate.update(
                "UPDATE refresh_tokens SET consumed_at = ?, replaced_by_token_id = ? "
                        + "WHERE id = ? AND consumed_at IS NULL AND revoked_at IS NULL",
                Timestamp.from(now), nextTokenId, row.tokenId());
        if (consumed != 1) {
            throw new IllegalStateException("Locked refresh token could not be rotated.");
        }
        jdbcTemplate.update("UPDATE auth_sessions SET last_seen_at = ? WHERE id = ?",
                Timestamp.from(now), parsed.sessionId());
        return new RotationResult(RotationState.ROTATED, nextTokens);
    }

    private RefreshRow findForUpdate(ParsedRefreshToken parsed) {
        List<RefreshRow> rows = jdbcTemplate.query(
                "SELECT t.id AS token_id, s.user_id, t.expires_at AS token_expires_at, "
                        + "t.consumed_at, t.revoked_at AS token_revoked_at, s.expires_at AS session_expires_at, "
                        + "s.revoked_at AS session_revoked_at FROM refresh_tokens t "
                        + "JOIN auth_sessions s ON s.id = t.session_id "
                        + "WHERE t.session_id = ? AND t.token_hash = ? FOR UPDATE OF t",
                (resultSet, rowNumber) -> new RefreshRow(
                        resultSet.getObject("token_id", UUID.class),
                        resultSet.getObject("user_id", UUID.class),
                        instant(resultSet.getTimestamp("token_expires_at")),
                        instant(resultSet.getTimestamp("consumed_at")),
                        instant(resultSet.getTimestamp("token_revoked_at")),
                        instant(resultSet.getTimestamp("session_expires_at")),
                        instant(resultSet.getTimestamp("session_revoked_at"))),
                parsed.sessionId(), parsed.tokenHash());
        return rows.stream().findFirst().orElse(null);
    }

    private boolean lockSession(UUID sessionId) {
        List<UUID> sessions = jdbcTemplate.query(
                "SELECT id FROM auth_sessions WHERE id = ? FOR UPDATE",
                (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class), sessionId);
        return !sessions.isEmpty();
    }

    private boolean isUserActive(UUID userId) {
        TenantExecutionContext authentication = new TenantExecutionContext(
                userId, null, TenantActorScope.IDENTITY_AUTHENTICATION);
        try (TenantContextHolder.Scope ignored = contextHolder.install(authentication)) {
            databaseContext.applyCurrent();
            List<String> statuses = jdbcTemplate.query(
                    "SELECT status FROM users WHERE id = ?",
                    (resultSet, rowNumber) -> resultSet.getString(1), userId);
            return statuses.stream().anyMatch("ACTIVE"::equals);
        }
    }

    private void revokeFamily(UUID sessionId, Instant now, String reason) {
        jdbcTemplate.update(
                "UPDATE auth_sessions SET revoked_at = ?, revoke_reason = ? WHERE id = ? AND revoked_at IS NULL",
                Timestamp.from(now), reason, sessionId);
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET revoked_at = COALESCE(revoked_at, ?) WHERE session_id = ?",
                Timestamp.from(now), sessionId);
    }

    private TokenResponse tokenResponse(UUID userId, String refreshToken, Instant refreshExpiry, Instant now) {
        Instant accessExpiry = now.plus(accessTokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(accessExpiry)
                .id(UUID.randomUUID().toString())
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(accessToken, refreshToken, "Bearer", accessTokenTtl.toSeconds(), refreshExpiry);
    }

    private String createRefreshToken(UUID sessionId) {
        byte[] secret = new byte[32];
        secureRandom.nextBytes(secret);
        return sessionId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    private static ParsedRefreshToken parse(String rawToken) {
        if (rawToken == null || rawToken.length() > 256) {
            return null;
        }
        int separator = rawToken.indexOf('.');
        if (separator < 1 || separator != rawToken.lastIndexOf('.')) {
            return null;
        }
        try {
            UUID sessionId = UUID.fromString(rawToken.substring(0, separator));
            byte[] secret = Base64.getUrlDecoder().decode(rawToken.substring(separator + 1));
            if (secret.length != 32) {
                return null;
            }
            return new ParsedRefreshToken(sessionId, hashToken(rawToken));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static byte[] hashToken(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static byte[] hashDeviceId(String deviceId) {
        return hashToken("localshop-device:" + deviceId);
    }

    private static Duration positive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
        return duration;
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record ParsedRefreshToken(UUID sessionId, byte[] tokenHash) { }
}
