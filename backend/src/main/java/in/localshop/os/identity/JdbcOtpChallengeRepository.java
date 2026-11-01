package in.localshop.os.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOtpChallengeRepository implements OtpChallengeRepository {

    private static final String COLUMNS = "id, phone_e164, purpose, code_hash, expires_at, "
            + "attempt_count, max_attempts, status";
    private static final RowMapper<OtpChallenge> CHALLENGE_MAPPER = JdbcOtpChallengeRepository::mapChallenge;

    private final JdbcTemplate jdbcTemplate;

    public JdbcOtpChallengeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void create(OtpChallenge challenge) {
        jdbcTemplate.update(
                "INSERT INTO otp_challenges (id, phone_e164, purpose, code_hash, expires_at, "
                        + "attempt_count, max_attempts, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                challenge.id(), challenge.phoneE164(), challenge.purpose().name(), challenge.codeHash(),
                Timestamp.from(challenge.expiresAt()), challenge.attemptCount(), challenge.maxAttempts(),
                challenge.status().name());
    }

    @Override
    public void markSent(UUID id, Instant sentAt) {
        int updated = jdbcTemplate.update(
                "UPDATE otp_challenges SET status = 'SENT', sent_at = ?, updated_at = NOW() "
                        + "WHERE id = ? AND status = 'PENDING' AND expires_at > ?",
                Timestamp.from(sentAt), id, Timestamp.from(sentAt));
        if (updated != 1) {
            throw new IllegalStateException("OTP challenge could not transition to SENT.");
        }
    }

    @Override
    public void markDeliveryFailed(UUID id) {
        jdbcTemplate.update(
                "UPDATE otp_challenges SET status = 'DELIVERY_FAILED', updated_at = NOW() "
                        + "WHERE id = ? AND status = 'PENDING'",
                id);
    }

    @Override
    public Optional<OtpChallenge> findById(UUID id) {
        List<OtpChallenge> rows = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM otp_challenges WHERE id = ?", CHALLENGE_MAPPER, id);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<OtpChallengeStatus> recordFailedAttempt(UUID id, Instant attemptedAt) {
        List<String> statuses = jdbcTemplate.query(
                "UPDATE otp_challenges SET attempt_count = attempt_count + 1, "
                        + "status = CASE WHEN attempt_count + 1 >= max_attempts THEN 'LOCKED' ELSE status END, "
                        + "updated_at = NOW() WHERE id = ? AND status = 'SENT' AND expires_at > ? "
                        + "AND attempt_count < max_attempts RETURNING status",
                (resultSet, row) -> resultSet.getString(1), id, Timestamp.from(attemptedAt));
        if (!statuses.isEmpty()) {
            return Optional.of(OtpChallengeStatus.valueOf(statuses.getFirst()));
        }
        return findById(id).map(OtpChallenge::status);
    }

    @Override
    public void markExpired(UUID id, Instant now) {
        jdbcTemplate.update(
                "UPDATE otp_challenges SET status = 'EXPIRED', updated_at = NOW() "
                        + "WHERE id = ? AND status = 'SENT' AND expires_at <= ?",
                id, Timestamp.from(now));
    }

    @Override
    public boolean consume(UUID id, Instant consumedAt) {
        return jdbcTemplate.update(
                "UPDATE otp_challenges SET status = 'CONSUMED', consumed_at = ?, updated_at = NOW() "
                        + "WHERE id = ? AND status = 'SENT' AND expires_at > ? AND attempt_count < max_attempts",
                Timestamp.from(consumedAt), id, Timestamp.from(consumedAt)) == 1;
    }

    private static OtpChallenge mapChallenge(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OtpChallenge(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("phone_e164"),
                OtpPurpose.valueOf(resultSet.getString("purpose")),
                resultSet.getBytes("code_hash"),
                resultSet.getTimestamp("expires_at").toInstant(),
                resultSet.getInt("attempt_count"),
                resultSet.getInt("max_attempts"),
                OtpChallengeStatus.valueOf(resultSet.getString("status")));
    }
}
