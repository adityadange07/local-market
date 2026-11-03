CREATE TABLE customer_addresses (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    label VARCHAR(50) NOT NULL,
    recipient_name VARCHAR(120) NOT NULL,
    recipient_phone VARCHAR(16),
    address_line1 VARCHAR(180) NOT NULL,
    address_line2 VARCHAR(180),
    landmark VARCHAR(120),
    locality VARCHAR(120) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    postal_code VARCHAR(6) NOT NULL,
    location GEOGRAPHY(POINT, 4326),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_customer_addresses_label CHECK (length(btrim(label)) > 0),
    CONSTRAINT ck_customer_addresses_recipient CHECK (length(btrim(recipient_name)) > 0),
    CONSTRAINT ck_customer_addresses_phone CHECK (recipient_phone IS NULL OR recipient_phone ~ '^\+[1-9][0-9]{1,14}$'),
    CONSTRAINT ck_customer_addresses_postal CHECK (postal_code ~ '^[0-9]{6}$'),
    CONSTRAINT ck_customer_addresses_location CHECK (
        location IS NULL OR (ST_Y(location::geometry) BETWEEN -90 AND 90 AND ST_X(location::geometry) BETWEEN -180 AND 180)
    ),
    CONSTRAINT ck_customer_addresses_lines CHECK (
        length(btrim(address_line1)) > 0 AND length(btrim(locality)) > 0
        AND length(btrim(city)) > 0 AND length(btrim(state)) > 0
    ),
    CONSTRAINT ck_customer_addresses_deleted_default CHECK (deleted_at IS NULL OR NOT is_default)
);

CREATE INDEX ix_customer_addresses_owner ON customer_addresses (user_id, created_at DESC, id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_customer_addresses_one_default ON customer_addresses (user_id) WHERE is_default AND deleted_at IS NULL;
CREATE INDEX ix_customer_addresses_location ON customer_addresses USING GIST (location) WHERE deleted_at IS NULL;

ALTER TABLE customer_addresses ENABLE ROW LEVEL SECURITY;
ALTER TABLE customer_addresses FORCE ROW LEVEL SECURITY;
CREATE POLICY customer_addresses_select_own ON customer_addresses FOR SELECT
    USING (user_id = NULLIF(current_setting('app.user_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'customer');
CREATE POLICY customer_addresses_insert_own ON customer_addresses FOR INSERT
    WITH CHECK (user_id = NULLIF(current_setting('app.user_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'customer');
CREATE POLICY customer_addresses_update_own ON customer_addresses FOR UPDATE
    USING (user_id = NULLIF(current_setting('app.user_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'customer')
    WITH CHECK (user_id = NULLIF(current_setting('app.user_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'customer');

CREATE TABLE carts (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    tenant_id UUID NOT NULL,
    store_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_carts_store FOREIGN KEY (tenant_id, store_id)
        REFERENCES stores (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_carts_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_carts_status CHECK (status IN ('ACTIVE', 'ORDERED', 'ABANDONED'))
);

CREATE UNIQUE INDEX uq_carts_customer_active ON carts (customer_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_carts_tenant_status ON carts (tenant_id, status, updated_at DESC);

CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    cart_id UUID NOT NULL,
    product_variant_id UUID NOT NULL,
    quantity SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (tenant_id, cart_id)
        REFERENCES carts (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_cart_items_variant FOREIGN KEY (tenant_id, product_variant_id)
        REFERENCES product_variants (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_cart_items_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_cart_items_cart_variant UNIQUE (cart_id, product_variant_id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity BETWEEN 1 AND 100)
);

CREATE INDEX ix_cart_items_cart ON cart_items (tenant_id, cart_id, created_at, id);
CREATE INDEX ix_cart_items_variant ON cart_items (tenant_id, product_variant_id);

ALTER TABLE carts ENABLE ROW LEVEL SECURITY;
ALTER TABLE carts FORCE ROW LEVEL SECURITY;
CREATE POLICY carts_select_own ON carts FOR SELECT
    USING (customer_id = NULLIF(current_setting('app.user_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'customer');
CREATE POLICY carts_insert_own ON carts FOR INSERT
    WITH CHECK (customer_id = NULLIF(current_setting('app.user_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'customer');
CREATE POLICY carts_update_own ON carts FOR UPDATE
    USING (customer_id = NULLIF(current_setting('app.user_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'customer')
    WITH CHECK (customer_id = NULLIF(current_setting('app.user_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'customer');

ALTER TABLE cart_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE cart_items FORCE ROW LEVEL SECURITY;
CREATE POLICY cart_items_select_own ON cart_items FOR SELECT
    USING (current_setting('app.actor_scope', true) = 'customer' AND EXISTS (
        SELECT 1 FROM carts c WHERE c.id = cart_items.cart_id AND c.tenant_id = cart_items.tenant_id
          AND c.customer_id = NULLIF(current_setting('app.user_id', true), '')::UUID
    ));
CREATE POLICY cart_items_insert_own ON cart_items FOR INSERT
    WITH CHECK (current_setting('app.actor_scope', true) = 'customer' AND EXISTS (
        SELECT 1 FROM carts c WHERE c.id = cart_items.cart_id AND c.tenant_id = cart_items.tenant_id
          AND c.customer_id = NULLIF(current_setting('app.user_id', true), '')::UUID AND c.status = 'ACTIVE'
    ));
CREATE POLICY cart_items_update_own ON cart_items FOR UPDATE
    USING (current_setting('app.actor_scope', true) = 'customer' AND EXISTS (
        SELECT 1 FROM carts c WHERE c.id = cart_items.cart_id AND c.tenant_id = cart_items.tenant_id
          AND c.customer_id = NULLIF(current_setting('app.user_id', true), '')::UUID AND c.status = 'ACTIVE'
    ))
    WITH CHECK (current_setting('app.actor_scope', true) = 'customer' AND EXISTS (
        SELECT 1 FROM carts c WHERE c.id = cart_items.cart_id AND c.tenant_id = cart_items.tenant_id
          AND c.customer_id = NULLIF(current_setting('app.user_id', true), '')::UUID AND c.status = 'ACTIVE'
    ));
CREATE POLICY cart_items_delete_own ON cart_items FOR DELETE
    USING (current_setting('app.actor_scope', true) = 'customer' AND EXISTS (
        SELECT 1 FROM carts c WHERE c.id = cart_items.cart_id AND c.tenant_id = cart_items.tenant_id
          AND c.customer_id = NULLIF(current_setting('app.user_id', true), '')::UUID AND c.status = 'ACTIVE'
    ));

GRANT SELECT, INSERT, UPDATE ON customer_addresses, carts TO localshop_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON cart_items TO localshop_runtime;
