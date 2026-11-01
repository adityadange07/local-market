CREATE TABLE users (
    id UUID PRIMARY KEY,
    phone_e164 VARCHAR(16) NOT NULL,
    phone_verified_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_phone_e164 UNIQUE (phone_e164),
    CONSTRAINT ck_users_phone_e164 CHECK (phone_e164 ~ '^\+[1-9][0-9]{1,14}$'),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE INDEX ix_users_status ON users (status);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    scope VARCHAR(12) NOT NULL,
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_roles_code UNIQUE (code),
    CONSTRAINT uq_roles_id_scope UNIQUE (id, scope),
    CONSTRAINT ck_roles_code CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_roles_scope CHECK (scope IN ('GLOBAL', 'TENANT'))
);

CREATE INDEX ix_roles_scope ON roles (scope);

CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_permissions_code UNIQUE (code),
    CONSTRAINT ck_permissions_code CHECK (code ~ '^[a-z][a-z0-9_.]*$')
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id)
        REFERENCES roles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id)
        REFERENCES permissions (id) ON DELETE RESTRICT
);

CREATE INDEX ix_role_permissions_permission_id ON role_permissions (permission_id);

CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    role_scope VARCHAR(12) NOT NULL DEFAULT 'GLOBAL',
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_by UUID,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT ck_user_roles_global_scope CHECK (role_scope = 'GLOBAL'),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_roles_role_scope FOREIGN KEY (role_id, role_scope)
        REFERENCES roles (id, scope) ON DELETE RESTRICT,
    CONSTRAINT fk_user_roles_assigned_by FOREIGN KEY (assigned_by)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_user_roles_role_id ON user_roles (role_id);

-- Role and permission IDs are opaque; application code resolves these immutable codes.
INSERT INTO roles (id, code, display_name, scope, description)
VALUES
    ('00000000-0000-4000-8000-000000000001', 'CUSTOMER', 'Customer', 'GLOBAL', 'Customer access to owned profile and marketplace actions.'),
    ('00000000-0000-4000-8000-000000000002', 'PLATFORM_SUPPORT', 'Platform Support', 'GLOBAL', 'Limited platform support access.'),
    ('00000000-0000-4000-8000-000000000003', 'PLATFORM_ADMIN', 'Platform Admin', 'GLOBAL', 'MFA-protected platform administration.'),
    ('00000000-0000-4000-8000-000000000004', 'TENANT_OWNER', 'Shop Owner', 'TENANT', 'Full operational authority for one shop tenant.'),
    ('00000000-0000-4000-8000-000000000005', 'TENANT_MANAGER', 'Shop Manager', 'TENANT', 'Shop operations without owner transfer or billing authority.'),
    ('00000000-0000-4000-8000-000000000006', 'TENANT_STAFF', 'Shop Staff', 'TENANT', 'Assigned catalogue, inventory, and order operations.');

INSERT INTO permissions (id, code, description)
VALUES
    ('00000000-0000-4000-8000-000000000101', 'marketplace.read', 'Read approved public marketplace content.'),
    ('00000000-0000-4000-8000-000000000102', 'customer.profile.read', 'Read own customer profile.'),
    ('00000000-0000-4000-8000-000000000103', 'customer.profile.write', 'Update own customer profile.'),
    ('00000000-0000-4000-8000-000000000104', 'customer.address.read', 'Read own saved addresses.'),
    ('00000000-0000-4000-8000-000000000105', 'customer.address.write', 'Create and update own saved addresses.'),
    ('00000000-0000-4000-8000-000000000106', 'customer.cart.read', 'Read own cart.'),
    ('00000000-0000-4000-8000-000000000107', 'customer.cart.write', 'Create and update own cart.'),
    ('00000000-0000-4000-8000-000000000108', 'customer.orders.create', 'Create a single-shop customer order.'),
    ('00000000-0000-4000-8000-000000000109', 'customer.orders.read_own', 'Read own order history and status.'),
    ('00000000-0000-4000-8000-000000000110', 'customer.reviews.create_eligible', 'Create an eligible review for a completed order.'),
    ('00000000-0000-4000-8000-000000000111', 'customer.notifications.read', 'Read own notifications.'),
    ('00000000-0000-4000-8000-000000000112', 'customer.devices.manage', 'Register or revoke own device tokens.'),
    ('00000000-0000-4000-8000-000000000113', 'platform.support.read', 'Read assigned support records.'),
    ('00000000-0000-4000-8000-000000000114', 'platform.support.manage', 'Update low-risk support workflow records.'),
    ('00000000-0000-4000-8000-000000000115', 'platform.customers.read', 'Look up customer records for authorized support.'),
    ('00000000-0000-4000-8000-000000000116', 'platform.shops.read', 'Read shop information for support.'),
    ('00000000-0000-4000-8000-000000000117', 'platform.shops.review', 'Approve or reject shop applications.'),
    ('00000000-0000-4000-8000-000000000118', 'platform.categories.manage', 'Manage shared marketplace categories.'),
    ('00000000-0000-4000-8000-000000000119', 'platform.plans.manage', 'Manage subscription plans.'),
    ('00000000-0000-4000-8000-000000000120', 'platform.subscription_payments.verify', 'Verify manually reported subscription payments.'),
    ('00000000-0000-4000-8000-000000000121', 'platform.invoices.read', 'Read platform subscription invoices.'),
    ('00000000-0000-4000-8000-000000000122', 'platform.analytics.read', 'Read platform analytics.'),
    ('00000000-0000-4000-8000-000000000123', 'platform.audit.read', 'Read platform audit records.'),
    ('00000000-0000-4000-8000-000000000124', 'platform.config.manage', 'Manage platform configuration.'),
    ('00000000-0000-4000-8000-000000000125', 'tenant.store.read', 'Read own tenant store profile.'),
    ('00000000-0000-4000-8000-000000000126', 'tenant.store.manage', 'Manage own tenant store profile and fulfilment settings.'),
    ('00000000-0000-4000-8000-000000000127', 'tenant.catalog.read', 'Read own tenant catalogue.'),
    ('00000000-0000-4000-8000-000000000128', 'tenant.catalog.manage', 'Manage own tenant catalogue.'),
    ('00000000-0000-4000-8000-000000000129', 'tenant.inventory.read', 'Read own tenant inventory.'),
    ('00000000-0000-4000-8000-000000000130', 'tenant.inventory.manage', 'Adjust own tenant inventory.'),
    ('00000000-0000-4000-8000-000000000131', 'tenant.orders.read', 'Read own tenant orders.'),
    ('00000000-0000-4000-8000-000000000132', 'tenant.orders.manage', 'Update own tenant order workflow.'),
    ('00000000-0000-4000-8000-000000000133', 'tenant.reports.read', 'Read own tenant reports.'),
    ('00000000-0000-4000-8000-000000000134', 'tenant.members.read', 'Read own tenant membership.'),
    ('00000000-0000-4000-8000-000000000135', 'tenant.members.manage', 'Manage own tenant team membership.'),
    ('00000000-0000-4000-8000-000000000136', 'tenant.subscriptions.read', 'Read own tenant subscription and invoices.'),
    ('00000000-0000-4000-8000-000000000137', 'tenant.subscriptions.manage', 'Manage own tenant subscription lifecycle.');

INSERT INTO role_permissions (role_id, permission_id)
SELECT role_row.id, permission_row.id
FROM roles AS role_row
CROSS JOIN permissions AS permission_row
WHERE role_row.code = 'CUSTOMER'
  AND permission_row.code IN (
      'marketplace.read',
      'customer.profile.read', 'customer.profile.write',
      'customer.address.read', 'customer.address.write',
      'customer.cart.read', 'customer.cart.write',
      'customer.orders.create', 'customer.orders.read_own',
      'customer.reviews.create_eligible', 'customer.notifications.read',
      'customer.devices.manage'
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT role_row.id, permission_row.id
FROM roles AS role_row
CROSS JOIN permissions AS permission_row
WHERE role_row.code = 'PLATFORM_SUPPORT'
  AND permission_row.code IN (
      'platform.support.read', 'platform.support.manage',
      'platform.customers.read', 'platform.shops.read'
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT role_row.id, permission_row.id
FROM roles AS role_row
CROSS JOIN permissions AS permission_row
WHERE role_row.code = 'PLATFORM_ADMIN'
  AND permission_row.code LIKE 'platform.%';

INSERT INTO role_permissions (role_id, permission_id)
SELECT role_row.id, permission_row.id
FROM roles AS role_row
CROSS JOIN permissions AS permission_row
WHERE role_row.code = 'TENANT_OWNER'
  AND permission_row.code LIKE 'tenant.%';

INSERT INTO role_permissions (role_id, permission_id)
SELECT role_row.id, permission_row.id
FROM roles AS role_row
CROSS JOIN permissions AS permission_row
WHERE role_row.code = 'TENANT_MANAGER'
  AND permission_row.code IN (
      'tenant.store.read', 'tenant.store.manage',
      'tenant.catalog.read', 'tenant.catalog.manage',
      'tenant.inventory.read', 'tenant.inventory.manage',
      'tenant.orders.read', 'tenant.orders.manage', 'tenant.reports.read'
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT role_row.id, permission_row.id
FROM roles AS role_row
CROSS JOIN permissions AS permission_row
WHERE role_row.code = 'TENANT_STAFF'
  AND permission_row.code IN (
      'tenant.store.read', 'tenant.catalog.read',
      'tenant.inventory.read', 'tenant.inventory.manage',
      'tenant.orders.read', 'tenant.orders.manage'
  );
