CREATE TABLE product_variants (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    product_id UUID NOT NULL,
    variant_code VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    sku VARCHAR(64),
    price_minor BIGINT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_product_variants_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES products (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_product_variants_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_product_variants_code UNIQUE (tenant_id, product_id, variant_code),
    CONSTRAINT ck_product_variants_code CHECK (variant_code ~ '^[A-Z0-9][A-Z0-9._-]{0,63}$'),
    CONSTRAINT ck_product_variants_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_product_variants_sku CHECK (sku IS NULL OR sku ~ '^[A-Z0-9][A-Z0-9._-]{0,63}$'),
    CONSTRAINT ck_product_variants_price CHECK (price_minor IS NULL OR price_minor > 0)
);

CREATE UNIQUE INDEX uq_product_variants_tenant_sku ON product_variants (tenant_id, sku) WHERE sku IS NOT NULL;
CREATE INDEX ix_product_variants_product ON product_variants (tenant_id, product_id, is_active);

CREATE TABLE inventory (
    tenant_id UUID NOT NULL,
    product_variant_id UUID NOT NULL,
    quantity_on_hand INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, product_variant_id),
    CONSTRAINT fk_inventory_variant FOREIGN KEY (tenant_id, product_variant_id)
        REFERENCES product_variants (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_quantity CHECK (quantity_on_hand >= 0)
);

CREATE TABLE stock_movements (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    product_variant_id UUID NOT NULL,
    movement_type VARCHAR(20) NOT NULL,
    quantity_delta INTEGER NOT NULL,
    quantity_before INTEGER NOT NULL,
    quantity_after INTEGER NOT NULL,
    reason VARCHAR(240) NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_stock_movements_variant FOREIGN KEY (tenant_id, product_variant_id)
        REFERENCES product_variants (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_stock_movement_type CHECK (movement_type IN ('ADJUSTMENT')),
    CONSTRAINT ck_stock_movement_delta CHECK (quantity_delta <> 0),
    CONSTRAINT ck_stock_movement_quantities CHECK (
        quantity_before >= 0 AND quantity_after >= 0
        AND quantity_after = quantity_before + quantity_delta
    ),
    CONSTRAINT ck_stock_movement_reason CHECK (length(btrim(reason)) BETWEEN 1 AND 240)
);

CREATE INDEX ix_stock_movements_tenant_variant_created
    ON stock_movements (tenant_id, product_variant_id, created_at DESC, id DESC);

CREATE FUNCTION reject_stock_movement_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'stock movement records are immutable' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_stock_movements_immutable
    BEFORE UPDATE OR DELETE ON stock_movements
    FOR EACH ROW EXECUTE FUNCTION reject_stock_movement_mutation();

ALTER TABLE product_variants ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_variants FORCE ROW LEVEL SECURITY;
CREATE POLICY product_variants_tenant_select ON product_variants FOR SELECT
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller');
CREATE POLICY product_variants_tenant_insert ON product_variants FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller');
CREATE POLICY product_variants_tenant_update ON product_variants FOR UPDATE
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller')
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller');

ALTER TABLE inventory ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory FORCE ROW LEVEL SECURITY;
CREATE POLICY inventory_tenant_select ON inventory FOR SELECT
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller');
CREATE POLICY inventory_tenant_insert ON inventory FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller');
CREATE POLICY inventory_tenant_update ON inventory FOR UPDATE
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller')
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller');

ALTER TABLE stock_movements ENABLE ROW LEVEL SECURITY;
ALTER TABLE stock_movements FORCE ROW LEVEL SECURITY;
CREATE POLICY stock_movements_tenant_select ON stock_movements FOR SELECT
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller');
CREATE POLICY stock_movements_tenant_insert ON stock_movements FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller');

GRANT SELECT, INSERT, UPDATE ON product_variants, inventory TO localshop_runtime;
GRANT SELECT, INSERT ON stock_movements TO localshop_runtime;
