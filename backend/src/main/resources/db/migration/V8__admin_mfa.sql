CREATE TABLE admin_mfa_credentials (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,
    secret_ciphertext BYTEA NOT NULL,
    pending_expires_at TIMESTAMPTZ,
    enabled_at TIMESTAMPTZ,
    last_verified_step BIGINT,
    attempt_window_started_at TIMESTAMPTZ,
    failed_attempts SMALLINT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_admin_mfa_state CHECK (
        (enabled_at IS NULL AND pending_expires_at IS NOT NULL)
        OR (enabled_at IS NOT NULL AND pending_expires_at IS NULL)
    ),
    CONSTRAINT ck_admin_mfa_step CHECK (last_verified_step IS NULL OR last_verified_step >= 0),
    CONSTRAINT ck_admin_mfa_failed_attempts CHECK (failed_attempts BETWEEN 0 AND 5)
);

CREATE TABLE admin_mfa_recovery_codes (
    user_id UUID NOT NULL REFERENCES admin_mfa_credentials(user_id) ON DELETE RESTRICT,
    code_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    used_at TIMESTAMPTZ,
    CONSTRAINT pk_admin_mfa_recovery_codes PRIMARY KEY (user_id, code_hash)
);

CREATE INDEX ix_admin_mfa_recovery_codes_unused
    ON admin_mfa_recovery_codes (user_id, created_at) WHERE used_at IS NULL;

CREATE TABLE admin_mfa_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    event_type VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_admin_mfa_event_type CHECK (event_type IN (
        'ENROLLMENT_STARTED', 'ENROLLMENT_COMPLETED', 'TOTP_VERIFIED', 'RECOVERY_CODE_USED', 'VERIFICATION_FAILED'
    ))
);

CREATE INDEX ix_admin_mfa_events_user_created ON admin_mfa_events (user_id, created_at DESC);

ALTER TABLE admin_mfa_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE admin_mfa_credentials FORCE ROW LEVEL SECURITY;
CREATE POLICY admin_mfa_credentials_self_admin ON admin_mfa_credentials
    USING (user_id = NULLIF(current_setting('app.user_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'platform_admin')
    WITH CHECK (user_id = NULLIF(current_setting('app.user_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'platform_admin');

ALTER TABLE admin_mfa_recovery_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE admin_mfa_recovery_codes FORCE ROW LEVEL SECURITY;
CREATE POLICY admin_mfa_recovery_codes_self_admin ON admin_mfa_recovery_codes
    USING (user_id = NULLIF(current_setting('app.user_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'platform_admin')
    WITH CHECK (user_id = NULLIF(current_setting('app.user_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'platform_admin');

ALTER TABLE admin_mfa_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE admin_mfa_events FORCE ROW LEVEL SECURITY;
CREATE POLICY admin_mfa_events_insert_actor ON admin_mfa_events FOR INSERT
    WITH CHECK (user_id = NULLIF(current_setting('app.user_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'platform_admin');

GRANT SELECT, INSERT, UPDATE ON admin_mfa_credentials, admin_mfa_recovery_codes TO localshop_runtime;
GRANT INSERT ON admin_mfa_events TO localshop_runtime;
