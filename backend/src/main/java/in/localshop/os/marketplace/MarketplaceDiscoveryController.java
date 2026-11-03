package in.localshop.os.marketplace;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/marketplace")
public class MarketplaceDiscoveryController {
    private final MarketplaceDiscoveryService discovery;

    public MarketplaceDiscoveryController(MarketplaceDiscoveryService discovery) { this.discovery = discovery; }

    @GetMapping("/shops/nearby")
    public MarketplaceSearchPage<NearbyShopView> nearby(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5000") int radiusMeters,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return discovery.nearby(latitude, longitude, radiusMeters, limit, offset);
    }

    @GetMapping("/shops/search")
    public MarketplaceSearchPage<ShopSearchResult> searchShops(
            @RequestParam String q,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return discovery.searchShops(q, city, limit, offset);
    }

    @GetMapping("/products/search")
    public MarketplaceSearchPage<ProductSearchResult> searchProducts(
            @RequestParam String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return discovery.searchProducts(q, category, city, limit, offset);
    }
}
