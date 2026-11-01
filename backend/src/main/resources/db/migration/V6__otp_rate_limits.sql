CREATE TABLE otp_rate_limits (
    dimension VARCHAR(40) NOT NULL,
    subject_hash BYTEA NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_otp_rate_limits PRIMARY KEY (dimension, subject_hash),
    CONSTRAINT ck_otp_rate_limit_subject_hash CHECK (octet_length(subject_hash) = 32),
    CONSTRAINT ck_otp_rate_limit_count CHECK (request_count > 0),
    CONSTRAINT ck_otp_rate_limit_dimension CHECK (dimension IN (
        'REQUEST_PHONE_COOLDOWN', 'REQUEST_PHONE_WINDOW', 'REQUEST_IP_WINDOW', 'REQUEST_DEVICE_WINDOW',
        'VERIFY_PHONE_WINDOW', 'VERIFY_IP_WINDOW', 'VERIFY_DEVICE_WINDOW'
    ))
);

CREATE INDEX ix_otp_rate_limits_expiry ON otp_rate_limits (window_started_at);

-- Remove rate-limit buckets older than one day from the scheduled cleanup job.
GRANT SELECT, INSERT, UPDATE ON TABLE otp_rate_limits TO localshop_runtime;
