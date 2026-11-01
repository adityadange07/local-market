CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    tenant_id UUID REFERENCES tenants(id) ON DELETE RESTRICT,
    actor_scope VARCHAR(32) NOT NULL,
    action_code VARCHAR(100) NOT NULL,
    object_type VARCHAR(80) NOT NULL,
    object_id VARCHAR(160),
    correlation_id UUID,
    change_summary JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_audit_actor_scope CHECK (actor_scope IN (
        'customer', 'seller', 'platform_admin', 'system', 'identity_authentication', 'identity_provisioning'
    )),
    CONSTRAINT ck_audit_action_code CHECK (action_code ~ '^[a-z][a-z0-9_.]{1,99}$'),
    CONSTRAINT ck_audit_object_type CHECK (object_type ~ '^[a-z][a-z0-9_.]{1,79}$'),
    CONSTRAINT ck_audit_summary_object CHECK (jsonb_typeof(change_summary) = 'object')
);

CREATE INDEX ix_audit_logs_created_at ON audit_logs (created_at DESC, id DESC);
CREATE INDEX ix_audit_logs_actor_created ON audit_logs (actor_user_id, created_at DESC)
    WHERE actor_user_id IS NOT NULL;
CREATE INDEX ix_audit_logs_tenant_created ON audit_logs (tenant_id, created_at DESC)
    WHERE tenant_id IS NOT NULL;
CREATE INDEX ix_audit_logs_object_created ON audit_logs (object_type, object_id, created_at DESC);
CREATE INDEX ix_audit_logs_correlation ON audit_logs (correlation_id) WHERE correlation_id IS NOT NULL;

ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs FORCE ROW LEVEL SECURITY;
CREATE POLICY audit_logs_insert_current_actor ON audit_logs FOR INSERT
    WITH CHECK (
        actor_user_id = NULLIF(current_setting('app.user_id', true), '')::UUID
        AND actor_scope = current_setting('app.actor_scope', true)
        AND (tenant_id IS NULL OR tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
             OR actor_scope = 'platform_admin')
    );
CREATE POLICY audit_logs_select_platform_admin ON audit_logs FOR SELECT
    USING (current_setting('app.actor_scope', true) = 'platform_admin');

GRANT INSERT, SELECT ON audit_logs TO localshop_runtime;
