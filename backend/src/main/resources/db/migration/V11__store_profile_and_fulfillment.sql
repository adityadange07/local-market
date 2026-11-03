CREATE TABLE store_addresses (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    store_id UUID NOT NULL,
    address_line1 VARCHAR(180) NOT NULL,
    address_line2 VARCHAR(180),
    landmark VARCHAR(120),
    locality VARCHAR(120) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    postal_code VARCHAR(6) NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_store_addresses_tenant_store UNIQUE (tenant_id, store_id),
    CONSTRAINT fk_store_addresses_store FOREIGN KEY (tenant_id, store_id)
        REFERENCES stores (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_store_addresses_line1 CHECK (length(btrim(address_line1)) > 0),
    CONSTRAINT ck_store_addresses_locality CHECK (length(btrim(locality)) > 0),
    CONSTRAINT ck_store_addresses_city CHECK (length(btrim(city)) > 0),
    CONSTRAINT ck_store_addresses_state CHECK (length(btrim(state)) > 0),
    CONSTRAINT ck_store_addresses_postal_code CHECK (postal_code ~ '^[0-9]{6}$'),
    CONSTRAINT ck_store_addresses_location CHECK (
        ST_Y(location::geometry) BETWEEN -90 AND 90 AND ST_X(location::geometry) BETWEEN -180 AND 180
    )
);

CREATE INDEX ix_store_addresses_location ON store_addresses USING GIST (location);
CREATE INDEX ix_store_addresses_tenant_store ON store_addresses (tenant_id, store_id);

CREATE TABLE store_hours (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    store_id UUID NOT NULL,
    day_of_week SMALLINT NOT NULL,
    opens_at TIME,
    closes_at TIME,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_store_hours_tenant_store_day UNIQUE (tenant_id, store_id, day_of_week),
    CONSTRAINT fk_store_hours_store FOREIGN KEY (tenant_id, store_id)
        REFERENCES stores (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_store_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_store_hours_window CHECK (
        (is_closed AND opens_at IS NULL AND closes_at IS NULL)
        OR (NOT is_closed AND opens_at IS NOT NULL AND closes_at IS NOT NULL AND closes_at > opens_at)
    )
);

CREATE INDEX ix_store_hours_store_day ON store_hours (tenant_id, store_id, day_of_week);

CREATE TABLE store_fulfillment_settings (
    tenant_id UUID NOT NULL,
    store_id UUID NOT NULL,
    delivery_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    pickup_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    delivery_radius_meters INTEGER,
    minimum_order_minor BIGINT NOT NULL DEFAULT 0,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_store_fulfillment_settings PRIMARY KEY (tenant_id, store_id),
    CONSTRAINT fk_store_fulfillment_store FOREIGN KEY (tenant_id, store_id)
        REFERENCES stores (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_store_fulfillment_method CHECK (delivery_enabled OR pickup_enabled),
    CONSTRAINT ck_store_fulfillment_radius CHECK (
        (delivery_enabled AND delivery_radius_meters BETWEEN 100 AND 50000)
        OR (NOT delivery_enabled AND delivery_radius_meters IS NULL)
    ),
    CONSTRAINT ck_store_fulfillment_minimum CHECK (minimum_order_minor >= 0)
);

INSERT INTO store_fulfillment_settings (tenant_id, store_id)
SELECT tenant_id, id FROM stores;

ALTER TABLE store_addresses ENABLE ROW LEVEL SECURITY;
ALTER TABLE store_addresses FORCE ROW LEVEL SECURITY;
CREATE POLICY store_addresses_select_scoped_or_public ON store_addresses FOR SELECT
    USING (
        (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
            AND current_setting('app.actor_scope', true) IN ('seller', 'tenant_onboarding'))
        OR current_setting('app.actor_scope', true) = 'platform_admin'
        OR EXISTS (
            SELECT 1 FROM stores s JOIN tenants t ON t.id = s.tenant_id
            WHERE s.id = store_addresses.store_id AND s.tenant_id = store_addresses.tenant_id
              AND s.status = 'PUBLISHED' AND t.status = 'APPROVED'
        )
    );
CREATE POLICY store_addresses_insert_seller ON store_addresses FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller');
CREATE POLICY store_addresses_update_seller ON store_addresses FOR UPDATE
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller')
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller');

ALTER TABLE store_hours ENABLE ROW LEVEL SECURITY;
ALTER TABLE store_hours FORCE ROW LEVEL SECURITY;
CREATE POLICY store_hours_select_scoped_or_public ON store_hours FOR SELECT
    USING (
        (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
            AND current_setting('app.actor_scope', true) IN ('seller', 'tenant_onboarding'))
        OR current_setting('app.actor_scope', true) = 'platform_admin'
        OR EXISTS (
            SELECT 1 FROM stores s JOIN tenants t ON t.id = s.tenant_id
            WHERE s.id = store_hours.store_id AND s.tenant_id = store_hours.tenant_id
              AND s.status = 'PUBLISHED' AND t.status = 'APPROVED'
        )
    );
CREATE POLICY store_hours_insert_seller ON store_hours FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller');
CREATE POLICY store_hours_update_seller ON store_hours FOR UPDATE
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller')
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller');

ALTER TABLE store_fulfillment_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE store_fulfillment_settings FORCE ROW LEVEL SECURITY;
CREATE POLICY store_fulfillment_select_scoped_or_public ON store_fulfillment_settings FOR SELECT
    USING (
        (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
            AND current_setting('app.actor_scope', true) IN ('seller', 'tenant_onboarding'))
        OR current_setting('app.actor_scope', true) = 'platform_admin'
        OR EXISTS (
            SELECT 1 FROM stores s JOIN tenants t ON t.id = s.tenant_id
            WHERE s.id = store_fulfillment_settings.store_id AND s.tenant_id = store_fulfillment_settings.tenant_id
              AND s.status = 'PUBLISHED' AND t.status = 'APPROVED'
        )
    );
CREATE POLICY store_fulfillment_insert_seller ON store_fulfillment_settings FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller');
CREATE POLICY store_fulfillment_update_seller ON store_fulfillment_settings FOR UPDATE
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller')
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller');

GRANT SELECT, INSERT, UPDATE ON store_addresses, store_hours, store_fulfillment_settings TO localshop_runtime;
