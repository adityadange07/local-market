CREATE TABLE otp_challenges (
    id UUID PRIMARY KEY,
    phone_e164 VARCHAR(16) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    code_hash BYTEA NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    attempt_count SMALLINT NOT NULL DEFAULT 0,
    max_attempts SMALLINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_otp_phone_e164 CHECK (phone_e164 ~ '^\+[1-9][0-9]{1,14}$'),
    CONSTRAINT ck_otp_purpose CHECK (purpose IN ('CUSTOMER_LOGIN', 'SELLER_LOGIN', 'SELLER_REGISTRATION')),
    CONSTRAINT ck_otp_hash_length CHECK (octet_length(code_hash) = 32),
    CONSTRAINT ck_otp_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_otp_max_attempts CHECK (max_attempts BETWEEN 1 AND 10),
    CONSTRAINT ck_otp_status CHECK (
        status IN ('PENDING', 'SENT', 'DELIVERY_FAILED', 'CONSUMED', 'EXPIRED', 'LOCKED')
    ),
    CONSTRAINT ck_otp_sent_state CHECK (
        (status IN ('SENT', 'CONSUMED', 'EXPIRED', 'LOCKED') AND sent_at IS NOT NULL)
        OR (status IN ('PENDING', 'DELIVERY_FAILED') AND sent_at IS NULL)
    ),
    CONSTRAINT ck_otp_consumed_state CHECK (
        (status = 'CONSUMED' AND consumed_at IS NOT NULL)
        OR (status <> 'CONSUMED' AND consumed_at IS NULL)
    ),
    CONSTRAINT ck_otp_attempts_within_limit CHECK (attempt_count <= max_attempts)
);

CREATE INDEX ix_otp_challenges_phone_created_at
    ON otp_challenges (phone_e164, created_at DESC);
CREATE INDEX ix_otp_challenges_expiry
    ON otp_challenges (expires_at)
    WHERE status IN ('PENDING', 'SENT');
CREATE INDEX ix_otp_challenges_retention
    ON otp_challenges (created_at)
    WHERE status IN ('DELIVERY_FAILED', 'CONSUMED', 'EXPIRED', 'LOCKED');

-- Challenge rows contain no plaintext codes. Purge terminal rows older than 30 days in the scheduled-job phase.
GRANT INSERT, UPDATE ON TABLE otp_challenges TO localshop_runtime;
