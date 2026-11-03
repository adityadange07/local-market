CREATE TABLE product_images (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    product_id UUID NOT NULL,
    object_key VARCHAR(200) NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    byte_size INTEGER NOT NULL,
    sha256_hex CHAR(64) NOT NULL,
    scan_status VARCHAR(16) NOT NULL DEFAULT 'PENDING_SCAN',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_product_images_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES products (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_product_images_object_key UNIQUE (object_key),
    CONSTRAINT uq_product_images_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_product_images_object_key CHECK (object_key ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}$'),
    CONSTRAINT ck_product_images_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_product_images_size CHECK (byte_size BETWEEN 1 AND 5242880),
    CONSTRAINT ck_product_images_sha CHECK (sha256_hex ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_product_images_scan_status CHECK (scan_status IN ('PENDING_SCAN', 'CLEAN', 'REJECTED')),
    CONSTRAINT ck_product_images_order CHECK (sort_order >= 0)
);

CREATE INDEX ix_product_images_product_order ON product_images (tenant_id, product_id, sort_order, id);
CREATE INDEX ix_product_images_scan_pending ON product_images (created_at, id) WHERE scan_status = 'PENDING_SCAN';

ALTER TABLE product_images ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_images FORCE ROW LEVEL SECURITY;
CREATE POLICY product_images_select_tenant_or_public ON product_images FOR SELECT
    USING (
        (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
            AND current_setting('app.actor_scope', true) = 'seller')
        OR current_setting('app.actor_scope', true) = 'platform_admin'
        OR (
            scan_status = 'CLEAN'
            AND EXISTS (
                SELECT 1 FROM products p
                JOIN stores s ON s.id = p.store_id AND s.tenant_id = p.tenant_id
                JOIN tenants t ON t.id = s.tenant_id
                WHERE p.id = product_images.product_id AND p.tenant_id = product_images.tenant_id
                  AND p.status = 'PUBLISHED' AND s.status = 'PUBLISHED' AND t.status = 'APPROVED'
            )
        )
    );
CREATE POLICY product_images_insert_seller ON product_images FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller');
CREATE POLICY product_images_delete_seller ON product_images FOR DELETE
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        AND current_setting('app.actor_scope', true) = 'seller');
CREATE POLICY product_images_update_admin ON product_images FOR UPDATE
    USING (current_setting('app.actor_scope', true) = 'platform_admin')
    WITH CHECK (current_setting('app.actor_scope', true) = 'platform_admin');

GRANT SELECT, INSERT, DELETE ON product_images TO localshop_runtime;
GRANT UPDATE (scan_status) ON product_images TO localshop_runtime;
