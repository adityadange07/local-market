CREATE POLICY product_variants_select_public ON product_variants FOR SELECT
    USING (is_active AND current_setting('app.actor_scope', true) IN ('public', 'customer')
        AND EXISTS (
            SELECT 1 FROM products p
            JOIN stores s ON s.id = p.store_id AND s.tenant_id = p.tenant_id
            JOIN tenants t ON t.id = s.tenant_id
            WHERE p.id = product_variants.product_id AND p.tenant_id = product_variants.tenant_id
              AND p.status = 'PUBLISHED' AND s.status = 'PUBLISHED' AND t.status = 'APPROVED'
        ));
