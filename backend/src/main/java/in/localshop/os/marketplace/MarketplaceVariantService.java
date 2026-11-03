package in.localshop.os.marketplace;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import in.localshop.os.tenancy.TenantActorScope;
import in.localshop.os.tenancy.TenantContextHolder;
import in.localshop.os.tenancy.TenantDatabaseContext;
import in.localshop.os.tenancy.TenantExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class MarketplaceVariantService {
    private final JdbcTemplate jdbc;
    private final TenantContextHolder contextHolder;
    private final TenantDatabaseContext databaseContext;
    private final TransactionTemplate transactions;

    public MarketplaceVariantService(JdbcTemplate jdbc, TenantContextHolder contextHolder,
            TenantDatabaseContext databaseContext, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.contextHolder = contextHolder;
        this.databaseContext = databaseContext;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setReadOnly(true);
    }

    public List<PublicVariantView> variants(String shopSlug, String productSlug) {
        return transactions.execute(status -> publicContext(() -> {
            List<PublicVariantView> variants = jdbc.query("SELECT v.id, v.variant_code, v.name, "
                            + "COALESCE(v.price_minor, p.price_minor) AS price_minor "
                            + "FROM product_variants v JOIN products p ON p.id = v.product_id AND p.tenant_id = v.tenant_id "
                            + "JOIN stores s ON s.id = p.store_id AND s.tenant_id = p.tenant_id "
                            + "JOIN tenants t ON t.id = s.tenant_id "
                            + "WHERE s.slug = ? AND p.slug = ? AND v.is_active AND p.status = 'PUBLISHED' "
                            + "AND s.status = 'PUBLISHED' AND t.status = 'APPROVED' ORDER BY v.variant_code, v.id",
                    (rs, row) -> new PublicVariantView(rs.getObject("id", UUID.class),
                            rs.getString("variant_code"), rs.getString("name"), rs.getLong("price_minor")),
                    shopSlug, productSlug);
            if (variants.isEmpty()) {
                List<Boolean> productExists = jdbc.query("SELECT EXISTS (SELECT 1 FROM products p "
                                + "JOIN stores s ON s.id = p.store_id AND s.tenant_id = p.tenant_id "
                                + "JOIN tenants t ON t.id = s.tenant_id WHERE s.slug = ? AND p.slug = ? "
                                + "AND p.status = 'PUBLISHED' AND s.status = 'PUBLISHED' AND t.status = 'APPROVED')",
                        (rs, row) -> rs.getBoolean(1), shopSlug, productSlug);
                if (productExists.isEmpty() || !productExists.getFirst()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
                }
            }
            return variants;
        }));
    }

    private <T> T publicContext(Supplier<T> action) {
        TenantExecutionContext context = new TenantExecutionContext(null, null, TenantActorScope.PUBLIC);
        try (TenantContextHolder.Scope ignored = contextHolder.install(context)) {
            databaseContext.applyCurrent();
            return action.get();
        }
    }
}
