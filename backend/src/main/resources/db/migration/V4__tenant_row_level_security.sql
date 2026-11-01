ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;

ALTER TABLE user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_roles FORCE ROW LEVEL SECURITY;

ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenants FORCE ROW LEVEL SECURITY;

ALTER TABLE tenant_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_members FORCE ROW LEVEL SECURITY;

ALTER TABLE stores ENABLE ROW LEVEL SECURITY;
ALTER TABLE stores FORCE ROW LEVEL SECURITY;

CREATE POLICY users_select_self_or_auth_or_admin
    ON users
    FOR SELECT
    USING (
        id = NULLIF(current_setting('app.user_id', true), '')::UUID
        OR current_setting('app.actor_scope', true) = 'identity_authentication'
        OR current_setting('app.actor_scope', true) = 'platform_admin'
    );

CREATE POLICY users_insert_verified_identity
    ON users
    FOR INSERT
    WITH CHECK (
        id = NULLIF(current_setting('app.user_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'identity_provisioning'
        AND phone_verified_at IS NOT NULL
        AND status = 'ACTIVE'
    );

CREATE POLICY users_update_platform_admin
    ON users
    FOR UPDATE
    USING (current_setting('app.actor_scope', true) = 'platform_admin')
    WITH CHECK (current_setting('app.actor_scope', true) = 'platform_admin');

CREATE POLICY user_roles_select_self_or_admin
    ON user_roles
    FOR SELECT
    USING (
        user_id = NULLIF(current_setting('app.user_id', true), '')::UUID
        OR current_setting('app.actor_scope', true) = 'platform_admin'
    );

CREATE POLICY user_roles_insert_customer_self
    ON user_roles
    FOR INSERT
    WITH CHECK (
        user_id = NULLIF(current_setting('app.user_id', true), '')::UUID
        AND role_scope = 'GLOBAL'
        AND current_setting('app.actor_scope', true) = 'identity_provisioning'
        AND role_id = (SELECT id FROM roles WHERE code = 'CUSTOMER')
    );

-- Approved tenant records are row-visible to support public store filtering. API DTOs must still select only public fields.
CREATE POLICY tenants_select_scoped_or_public
    ON tenants
    FOR SELECT
    USING (
        id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        OR current_setting('app.actor_scope', true) = 'platform_admin'
        OR status = 'APPROVED'
    );

CREATE POLICY tenants_insert_onboarding
    ON tenants
    FOR INSERT
    WITH CHECK (
        id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'tenant_onboarding'
        AND status = 'ONBOARDING'
    );

-- Separate policies keep seller updates in their current lifecycle state. Approval transitions are admin-only.
CREATE POLICY tenants_update_onboarding_seller
    ON tenants
    FOR UPDATE
    USING (
        id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller'
        AND status = 'ONBOARDING'
    )
    WITH CHECK (
        id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller'
        AND status = 'ONBOARDING'
    );

CREATE POLICY tenants_update_pending_seller
    ON tenants
    FOR UPDATE
    USING (
        id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller'
        AND status = 'PENDING_APPROVAL'
    )
    WITH CHECK (
        id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller'
        AND status = 'PENDING_APPROVAL'
    );

CREATE POLICY tenants_update_approved_seller
    ON tenants
    FOR UPDATE
    USING (
        id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller'
        AND status = 'APPROVED'
    )
    WITH CHECK (
        id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller'
        AND status = 'APPROVED'
    );

CREATE POLICY tenants_update_platform_admin
    ON tenants
    FOR UPDATE
    USING (current_setting('app.actor_scope', true) = 'platform_admin')
    WITH CHECK (current_setting('app.actor_scope', true) = 'platform_admin');

CREATE POLICY tenant_members_select_scoped_or_self
    ON tenant_members
    FOR SELECT
    USING (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        OR user_id = NULLIF(current_setting('app.user_id', true), '')::UUID
        OR current_setting('app.actor_scope', true) = 'platform_admin'
    );

CREATE POLICY tenant_members_insert_initial_owner
    ON tenant_members
    FOR INSERT
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND user_id = NULLIF(current_setting('app.user_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'tenant_onboarding'
        AND role_id = (SELECT id FROM roles WHERE code = 'TENANT_OWNER')
    );

CREATE POLICY tenant_members_insert_seller
    ON tenant_members
    FOR INSERT
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller'
    );

CREATE POLICY tenant_members_update_seller
    ON tenant_members
    FOR UPDATE
    USING (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller'
    );

CREATE POLICY tenant_members_update_platform_admin
    ON tenant_members
    FOR UPDATE
    USING (current_setting('app.actor_scope', true) = 'platform_admin')
    WITH CHECK (current_setting('app.actor_scope', true) = 'platform_admin');

CREATE POLICY stores_select_scoped_or_public
    ON stores
    FOR SELECT
    USING (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        OR current_setting('app.actor_scope', true) = 'platform_admin'
        OR (
            status = 'PUBLISHED'
            AND EXISTS (
                SELECT 1
                FROM tenants
                WHERE tenants.id = stores.tenant_id
                  AND tenants.status = 'APPROVED'
            )
        )
    );

CREATE POLICY stores_insert_onboarding
    ON stores
    FOR INSERT
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'tenant_onboarding'
        AND status = 'DRAFT'
    );

CREATE POLICY stores_insert_seller
    ON stores
    FOR INSERT
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller'
        AND status = 'DRAFT'
    );

CREATE POLICY stores_update_seller
    ON stores
    FOR UPDATE
    USING (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller'
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller'
    );

CREATE POLICY stores_update_platform_admin
    ON stores
    FOR UPDATE
    USING (current_setting('app.actor_scope', true) = 'platform_admin')
    WITH CHECK (current_setting('app.actor_scope', true) = 'platform_admin');

GRANT SELECT, INSERT, UPDATE ON users TO localshop_runtime;
GRANT SELECT, INSERT ON user_roles TO localshop_runtime;
GRANT SELECT ON roles, permissions, role_permissions TO localshop_runtime;
GRANT SELECT, INSERT, UPDATE ON tenants, tenant_members, stores TO localshop_runtime;
