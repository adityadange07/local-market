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
public class MarketplaceDiscoveryService {
    private static final int MAX_LIMIT = 100;
    private static final int MAX_OFFSET = 10_000;

    private final JdbcTemplate jdbc;
    private final TenantContextHolder contextHolder;
    private final TenantDatabaseContext databaseContext;
    private final TransactionTemplate transactions;

    public MarketplaceDiscoveryService(JdbcTemplate jdbc, TenantContextHolder contextHolder,
            TenantDatabaseContext databaseContext, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.contextHolder = contextHolder;
        this.databaseContext = databaseContext;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setReadOnly(true);
    }

    public MarketplaceSearchPage<NearbyShopView> nearby(double latitude, double longitude,
            int radiusMeters, int limit, int offset) {
        validateCoordinates(latitude, longitude);
        validatePage(limit, offset);
        if (radiusMeters < 100 || radiusMeters > 50_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Radius must be between 100 and 50000 meters.");
        }
        return transactions.execute(status -> publicContext(() -> {
            String point = "ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography";
            String predicate = "ST_DWithin(a.location, " + point + ", ?)";
            Long total = jdbc.queryForObject("SELECT count(*) FROM stores s "
                    + "JOIN tenants t ON t.id = s.tenant_id JOIN store_addresses a ON a.store_id = s.id "
                    + "AND a.tenant_id = s.tenant_id "
                    + "JOIN store_fulfillment_settings f ON f.store_id = s.id AND f.tenant_id = s.tenant_id "
                    + "WHERE t.status = 'APPROVED' AND s.status = 'PUBLISHED' AND " + predicate,
                    Long.class, longitude, latitude, radiusMeters);
            List<NearbyShopView> items = jdbc.query("SELECT s.id, s.slug, s.display_name, s.description, "
                            + "a.locality, a.city, a.postal_code, f.delivery_enabled, f.pickup_enabled, "
                            + "ST_Distance(a.location, " + point + ") AS distance_meters "
                            + "FROM stores s JOIN tenants t ON t.id = s.tenant_id "
                            + "JOIN store_addresses a ON a.store_id = s.id AND a.tenant_id = s.tenant_id "
                            + "JOIN store_fulfillment_settings f ON f.store_id = s.id AND f.tenant_id = s.tenant_id "
                            + "WHERE t.status = 'APPROVED' AND s.status = 'PUBLISHED' AND " + predicate
                            + " ORDER BY distance_meters, s.id LIMIT ? OFFSET ?",
                    (rs, row) -> new NearbyShopView(rs.getObject("id", UUID.class), rs.getString("slug"),
                            rs.getString("display_name"), rs.getString("description"), rs.getString("locality"),
                            rs.getString("city"), rs.getString("postal_code"), rs.getBoolean("delivery_enabled"),
                            rs.getBoolean("pickup_enabled"), rs.getDouble("distance_meters")),
                    longitude, latitude, longitude, latitude, radiusMeters, limit, offset);
            return new MarketplaceSearchPage<>(items, offset, limit, total == null ? 0 : total);
        }));
    }

    public MarketplaceSearchPage<ShopSearchResult> searchShops(String query, String city, int limit, int offset) {
        String term = requireQuery(query);
        validatePage(limit, offset);
        String cityFilter = normalizeOptional(city);
        String pattern = likePattern(term);
        String cityPattern = cityFilter == null ? null : likePattern(cityFilter);
        return transactions.execute(status -> publicContext(() -> {
            String predicate = "t.status = 'APPROVED' AND s.status = 'PUBLISHED' "
                    + "AND (s.display_name ILIKE ? ESCAPE '!' OR COALESCE(s.description, '') ILIKE ? ESCAPE '!' "
                    + "OR a.locality ILIKE ? ESCAPE '!' OR a.city ILIKE ? ESCAPE '!') "
                    + "AND (?::TEXT IS NULL OR a.city ILIKE ? ESCAPE '!')";
            Long total = jdbc.queryForObject("SELECT count(*) FROM stores s "
                    + "JOIN tenants t ON t.id = s.tenant_id JOIN store_addresses a ON a.store_id = s.id "
                    + "JOIN store_fulfillment_settings f ON f.store_id = s.id AND f.tenant_id = s.tenant_id "
                    + "WHERE " + predicate, Long.class, pattern, pattern, pattern, pattern, cityPattern, cityPattern);
            List<ShopSearchResult> items = jdbc.query("SELECT s.id, s.slug, s.display_name, s.description, "
                            + "a.locality, a.city, a.postal_code, f.delivery_enabled, f.pickup_enabled "
                            + "FROM stores s JOIN tenants t ON t.id = s.tenant_id "
                            + "JOIN store_addresses a ON a.store_id = s.id AND a.tenant_id = s.tenant_id "
                            + "JOIN store_fulfillment_settings f ON f.store_id = s.id AND f.tenant_id = s.tenant_id "
                            + "WHERE " + predicate + " ORDER BY s.display_name, s.id LIMIT ? OFFSET ?",
                    (rs, row) -> new ShopSearchResult(rs.getObject("id", UUID.class), rs.getString("slug"),
                            rs.getString("display_name"), rs.getString("description"), rs.getString("locality"),
                            rs.getString("city"), rs.getString("postal_code"), rs.getBoolean("delivery_enabled"),
                            rs.getBoolean("pickup_enabled")), pattern, pattern, pattern, pattern,
                    cityPattern, cityPattern, limit, offset);
            return new MarketplaceSearchPage<>(items, offset, limit, total == null ? 0 : total);
        }));
    }

    public MarketplaceSearchPage<ProductSearchResult> searchProducts(
            String query, String categorySlug, String city, int limit, int offset) {
        String term = requireQuery(query);
        validatePage(limit, offset);
        String category = normalizeOptional(categorySlug);
        String cityFilter = normalizeOptional(city);
        String pattern = likePattern(term);
        String cityPattern = cityFilter == null ? null : likePattern(cityFilter);
        return transactions.execute(status -> publicContext(() -> {
            String predicate = "t.status = 'APPROVED' AND s.status = 'PUBLISHED' AND p.status = 'PUBLISHED' "
                    + "AND c.is_active AND (p.name ILIKE ? ESCAPE '!' OR COALESCE(p.description, '') ILIKE ? ESCAPE '!') "
                    + "AND (?::TEXT IS NULL OR c.slug = ?) AND (?::TEXT IS NULL OR a.city ILIKE ? ESCAPE '!')";
            String from = " FROM products p JOIN categories c ON c.id = p.category_id "
                    + "JOIN stores s ON s.id = p.store_id AND s.tenant_id = p.tenant_id "
                    + "JOIN tenants t ON t.id = s.tenant_id "
                    + "JOIN store_addresses a ON a.store_id = s.id AND a.tenant_id = s.tenant_id ";
            Long total = jdbc.queryForObject("SELECT count(*)" + from + "WHERE " + predicate,
                    Long.class, pattern, pattern, category, category, cityPattern, cityPattern);
            List<ProductSearchResult> items = jdbc.query("SELECT p.id, p.slug, p.name, c.slug AS category_slug, "
                            + "c.name AS category_name, p.price_minor, s.slug AS shop_slug, "
                            + "s.display_name AS shop_name, a.city" + from + "WHERE " + predicate
                            + " ORDER BY p.name, s.display_name, p.id LIMIT ? OFFSET ?",
                    (rs, row) -> new ProductSearchResult(rs.getObject("id", UUID.class), rs.getString("slug"),
                            rs.getString("name"), rs.getString("category_slug"), rs.getString("category_name"),
                            rs.getLong("price_minor"), rs.getString("shop_slug"), rs.getString("shop_name"),
                            rs.getString("city")), pattern, pattern, category, category, cityPattern, cityPattern,
                    limit, offset);
            return new MarketplaceSearchPage<>(items, offset, limit, total == null ? 0 : total);
        }));
    }

    private <T> T publicContext(Supplier<T> operation) {
        TenantExecutionContext context = new TenantExecutionContext(null, null, TenantActorScope.PUBLIC);
        try (TenantContextHolder.Scope ignored = contextHolder.install(context)) {
            databaseContext.applyCurrent();
            return operation.get();
        }
    }

    private static void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid latitude and longitude are required.");
        }
    }

    private static void validatePage(int limit, int offset) {
        if (limit < 1 || limit > MAX_LIMIT || offset < 0 || offset > MAX_OFFSET) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page limit must be 1-100 and offset 0-10000.");
        }
    }

    private static String requireQuery(String query) {
        String value = normalizeOptional(query);
        if (value == null || value.length() < 2 || value.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must contain 2 to 100 characters.");
        }
        return value;
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static String likePattern(String value) {
        return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
