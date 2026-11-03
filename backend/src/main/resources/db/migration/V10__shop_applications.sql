INSERT INTO permissions (id, code, description)
VALUES ('00000000-0000-4000-8000-000000000138', 'tenant.applications.create',
        'Submit and view own shop applications.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'CUSTOMER' AND p.code = 'tenant.applications.create'
ON CONFLICT DO NOTHING;

CREATE POLICY tenants_select_member
    ON tenants FOR SELECT
    USING (EXISTS (
        SELECT 1 FROM tenant_members tm
        WHERE tm.tenant_id = tenants.id
          AND tm.user_id = NULLIF(current_setting('app.user_id', true), '')::UUID
          AND tm.status = 'ACTIVE'
    ));

CREATE POLICY tenants_submit_onboarding
    ON tenants FOR UPDATE
    USING (
        id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.user_id', true) = created_by::TEXT
        AND current_setting('app.actor_scope', true) = 'tenant_onboarding'
        AND status = 'ONBOARDING'
    )
    WITH CHECK (
        id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.user_id', true) = created_by::TEXT
        AND current_setting('app.actor_scope', true) = 'tenant_onboarding'
        AND status = 'PENDING_APPROVAL'
    );

CREATE POLICY stores_select_member
    ON stores FOR SELECT
    USING (EXISTS (
        SELECT 1 FROM tenant_members tm
        WHERE tm.tenant_id = stores.tenant_id
          AND tm.user_id = NULLIF(current_setting('app.user_id', true), '')::UUID
          AND tm.status = 'ACTIVE'
    ));

ALTER TABLE audit_logs DROP CONSTRAINT ck_audit_actor_scope;
ALTER TABLE audit_logs ADD CONSTRAINT ck_audit_actor_scope CHECK (actor_scope IN (
    'customer', 'seller', 'platform_admin', 'tenant_onboarding', 'system',
    'identity_authentication', 'identity_provisioning'
));
