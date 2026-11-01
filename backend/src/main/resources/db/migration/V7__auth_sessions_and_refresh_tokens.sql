CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    device_id_hash BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(32),
    CONSTRAINT ck_auth_session_device_hash CHECK (octet_length(device_id_hash) = 32),
    CONSTRAINT ck_auth_session_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_auth_session_revocation CHECK (
        (revoked_at IS NULL AND revoke_reason IS NULL)
        OR (revoked_at IS NOT NULL AND revoke_reason IN ('LOGOUT', 'REFRESH_REUSE', 'ACCOUNT_DISABLED', 'ADMIN_REVOKE'))
    ),
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_auth_sessions_user_active ON auth_sessions (user_id, expires_at DESC)
    WHERE revoked_at IS NULL;
CREATE INDEX ix_auth_sessions_user_device_active ON auth_sessions (user_id, device_id_hash)
    WHERE revoked_at IS NULL;
CREATE INDEX ix_auth_sessions_expiry ON auth_sessions (expires_at);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    token_hash BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    replaced_by_token_id UUID,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_token_hash CHECK (octet_length(token_hash) = 32),
    CONSTRAINT ck_refresh_token_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_refresh_token_rotation CHECK (
        (consumed_at IS NULL AND replaced_by_token_id IS NULL)
        OR (consumed_at IS NOT NULL AND replaced_by_token_id IS NOT NULL)
    ),
    CONSTRAINT fk_refresh_token_session FOREIGN KEY (session_id)
        REFERENCES auth_sessions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_refresh_token_replacement FOREIGN KEY (replaced_by_token_id)
        REFERENCES refresh_tokens (id) ON DELETE RESTRICT
);

CREATE INDEX ix_refresh_tokens_session ON refresh_tokens (session_id, created_at DESC);
CREATE INDEX ix_refresh_tokens_expiry ON refresh_tokens (expires_at);

GRANT SELECT, INSERT, UPDATE ON TABLE auth_sessions TO localshop_runtime;
GRANT SELECT, INSERT, UPDATE ON TABLE refresh_tokens TO localshop_runtime;
