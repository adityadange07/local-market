CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    account_name VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ONBOARDING',
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID,
    rejection_reason TEXT,
    created_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_tenants_account_name CHECK (length(btrim(account_name)) > 0),
    CONSTRAINT ck_tenants_status CHECK (
        status IN ('ONBOARDING', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'SUSPENDED', 'CLOSED')
    ),
    CONSTRAINT ck_tenants_review_actor_time CHECK (
        (reviewed_at IS NULL AND reviewed_by IS NULL)
        OR (reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL)
    ),
    CONSTRAINT ck_tenants_approval_has_review CHECK (
        status NOT IN ('APPROVED', 'REJECTED') OR reviewed_at IS NOT NULL
    ),
    CONSTRAINT ck_tenants_rejection_reason CHECK (
        (status = 'REJECTED' AND rejection_reason IS NOT NULL AND length(btrim(rejection_reason)) > 0)
        OR (status <> 'REJECTED' AND rejection_reason IS NULL)
    ),
    CONSTRAINT fk_tenants_reviewed_by FOREIGN KEY (reviewed_by)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tenants_created_by FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tenants_updated_by FOREIGN KEY (updated_by)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_tenants_status_created_at ON tenants (status, created_at DESC);
CREATE INDEX ix_tenants_reviewed_by ON tenants (reviewed_by) WHERE reviewed_by IS NOT NULL;

CREATE TABLE tenant_members (
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    role_scope VARCHAR(12) NOT NULL DEFAULT 'TENANT',
    status VARCHAR(16) NOT NULL DEFAULT 'INVITED',
    invited_by UUID,
    joined_at TIMESTAMPTZ,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_tenant_members PRIMARY KEY (tenant_id, user_id),
    CONSTRAINT ck_tenant_members_role_scope CHECK (role_scope = 'TENANT'),
    CONSTRAINT ck_tenant_members_status CHECK (
        status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'REMOVED')
    ),
    CONSTRAINT ck_tenant_members_joined_at CHECK (
        status NOT IN ('ACTIVE', 'SUSPENDED', 'REMOVED') OR joined_at IS NOT NULL
    ),
    CONSTRAINT fk_tenant_members_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tenant_members_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tenant_members_role_scope FOREIGN KEY (role_id, role_scope)
        REFERENCES roles (id, scope) ON DELETE RESTRICT,
    CONSTRAINT fk_tenant_members_invited_by FOREIGN KEY (invited_by)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tenant_members_updated_by FOREIGN KEY (updated_by)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_tenant_members_user_status ON tenant_members (user_id, status);
CREATE INDEX ix_tenant_members_role_id ON tenant_members (role_id);

CREATE TABLE stores (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    description TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_stores_tenant_id UNIQUE (tenant_id),
    CONSTRAINT uq_stores_public_slug UNIQUE (slug),
    CONSTRAINT uq_stores_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_stores_display_name CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT ck_stores_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_stores_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT fk_stores_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stores_created_by FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stores_updated_by FOREIGN KEY (updated_by)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_stores_status_updated_at ON stores (status, updated_at DESC);
