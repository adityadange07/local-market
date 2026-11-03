CREATE TABLE categories (
    id UUID PRIMARY KEY,
    parent_id UUID REFERENCES categories(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_categories_slug UNIQUE (slug),
    CONSTRAINT ck_categories_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_categories_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_categories_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_categories_not_self_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX ix_categories_active_order ON categories (sort_order, name) WHERE is_active;
CREATE INDEX ix_categories_parent ON categories (parent_id) WHERE parent_id IS NOT NULL;

INSERT INTO categories (id, name, slug, sort_order) VALUES
    ('00000000-0000-4000-8000-000000000201', 'Groceries', 'groceries', 10),
    ('00000000-0000-4000-8000-000000000202', 'Bakery and Sweets', 'bakery-sweets', 20),
    ('00000000-0000-4000-8000-000000000203', 'Clothing', 'clothing', 30),
    ('00000000-0000-4000-8000-000000000204', 'Footwear', 'footwear', 40),
    ('00000000-0000-4000-8000-000000000205', 'Electronics', 'electronics', 50),
    ('00000000-0000-4000-8000-000000000206', 'Home and Kitchen', 'home-kitchen', 60),
    ('00000000-0000-4000-8000-000000000207', 'Beauty and Personal Care', 'beauty-personal-care', 70),
    ('00000000-0000-4000-8000-000000000208', 'Books and Stationery', 'books-stationery', 80),
    ('00000000-0000-4000-8000-000000000209', 'Toys and Games', 'toys-games', 90);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    store_id UUID NOT NULL,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    sku VARCHAR(64),
    slug VARCHAR(100) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description TEXT,
    price_minor BIGINT NOT NULL,
    compare_at_price_minor BIGINT,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    updated_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    archived_at TIMESTAMPTZ,
    CONSTRAINT fk_products_store FOREIGN KEY (tenant_id, store_id)
        REFERENCES stores (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_products_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_products_tenant_slug UNIQUE (tenant_id, slug),
    CONSTRAINT ck_products_sku CHECK (sku IS NULL OR sku ~ '^[A-Z0-9][A-Z0-9._-]{0,63}$'),
    CONSTRAINT ck_products_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_products_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_products_price CHECK (price_minor > 0),
    CONSTRAINT ck_products_compare_price CHECK (
        compare_at_price_minor IS NULL OR compare_at_price_minor >= price_minor
    ),
    CONSTRAINT ck_products_currency CHECK (currency = 'INR'),
    CONSTRAINT ck_products_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_products_archive_state CHECK (
        (status = 'ARCHIVED' AND archived_at IS NOT NULL)
        OR (status <> 'ARCHIVED' AND archived_at IS NULL)
    )
);

CREATE UNIQUE INDEX uq_products_tenant_sku ON products (tenant_id, sku) WHERE sku IS NOT NULL;
CREATE INDEX ix_products_tenant_status_updated ON products (tenant_id, status, updated_at DESC);
CREATE INDEX ix_products_tenant_category_status ON products (tenant_id, category_id, status);
CREATE INDEX ix_products_store_public ON products (store_id, name, id) WHERE status = 'PUBLISHED';

ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE products FORCE ROW LEVEL SECURITY;
CREATE POLICY products_select_tenant_or_public ON products FOR SELECT
    USING (
        (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
            AND current_setting('app.actor_scope', true) = 'seller')
        OR current_setting('app.actor_scope', true) = 'platform_admin'
        OR EXISTS (
            SELECT 1 FROM stores s JOIN tenants t ON t.id = s.tenant_id
            WHERE s.id = products.store_id AND s.tenant_id = products.tenant_id
              AND s.status = 'PUBLISHED' AND t.status = 'APPROVED'
              AND products.status = 'PUBLISHED'
        )
    );
CREATE POLICY products_insert_seller ON products FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller');
CREATE POLICY products_update_seller ON products FOR UPDATE
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller')
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller');

GRANT SELECT ON categories TO localshop_runtime;
GRANT SELECT, INSERT, UPDATE ON products TO localshop_runtime;
