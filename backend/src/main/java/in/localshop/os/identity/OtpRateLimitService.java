package in.localshop.os.identity;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OtpRateLimitService {

    public enum Operation {
        REQUEST,
        VERIFY
    }

    private record Bucket(String dimension, String subject, int maximum, Duration window) { }

    private final JdbcTemplate jdbcTemplate;
    private final OtpCodeHasher codeHasher;
    private final TransactionTemplate transactionTemplate;

    public OtpRateLimitService(
            JdbcTemplate jdbcTemplate,
            OtpCodeHasher codeHasher,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.codeHasher = codeHasher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Atomically consumes every applicable bucket, rolling all of them back if any limit is exceeded. */
    public void checkAndConsume(Operation operation, String phone, String ipAddress, String deviceId, Instant now) {
        List<Bucket> buckets = operation == Operation.REQUEST
                ? List.of(
                        new Bucket("REQUEST_PHONE_COOLDOWN", phone, 1, Duration.ofMinutes(1)),
                        new Bucket("REQUEST_PHONE_WINDOW", phone, 5, Duration.ofMinutes(15)),
                        new Bucket("REQUEST_IP_WINDOW", ipAddress, 30, Duration.ofMinutes(15)),
                        new Bucket("REQUEST_DEVICE_WINDOW", deviceId, 10, Duration.ofMinutes(15)))
                : List.of(
                        new Bucket("VERIFY_PHONE_WINDOW", phone, 10, Duration.ofMinutes(15)),
                        new Bucket("VERIFY_IP_WINDOW", ipAddress, 60, Duration.ofMinutes(15)),
                        new Bucket("VERIFY_DEVICE_WINDOW", deviceId, 20, Duration.ofMinutes(15)));

        transactionTemplate.executeWithoutResult(status -> {
            for (Bucket bucket : buckets) {
                consume(bucket, now);
            }
        });
    }

    private void consume(Bucket bucket, Instant now) {
        byte[] subjectHash = codeHasher.hashRateLimitKey(bucket.dimension(), bucket.subject());
        Instant earliestWindowStart = now.minus(bucket.window());
        List<Integer> accepted = jdbcTemplate.query(
                "INSERT INTO otp_rate_limits (dimension, subject_hash, window_started_at, request_count) "
                        + "VALUES (?, ?, ?, 1) ON CONFLICT (dimension, subject_hash) DO UPDATE SET "
                        + "window_started_at = CASE WHEN otp_rate_limits.window_started_at <= ? "
                        + "THEN EXCLUDED.window_started_at ELSE otp_rate_limits.window_started_at END, "
                        + "request_count = CASE WHEN otp_rate_limits.window_started_at <= ? "
                        + "THEN 1 ELSE otp_rate_limits.request_count + 1 END, updated_at = NOW() "
                        + "WHERE otp_rate_limits.window_started_at <= ? OR otp_rate_limits.request_count < ? "
                        + "RETURNING request_count",
                (resultSet, rowNumber) -> resultSet.getInt(1),
                bucket.dimension(), subjectHash, Timestamp.from(now), Timestamp.from(earliestWindowStart),
                Timestamp.from(earliestWindowStart), Timestamp.from(earliestWindowStart), bucket.maximum());
        if (!accepted.isEmpty()) {
            return;
        }
        Instant windowStartedAt = jdbcTemplate.queryForObject(
                "SELECT window_started_at FROM otp_rate_limits WHERE dimension = ? AND subject_hash = ?",
                (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(),
                bucket.dimension(), subjectHash);
        long retryAfter = Duration.between(now, windowStartedAt.plus(bucket.window())).toSeconds();
        throw new OtpRateLimitException(retryAfter);
    }
}
