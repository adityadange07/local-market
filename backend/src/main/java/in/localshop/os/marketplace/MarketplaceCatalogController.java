package in.localshop.os.marketplace;

import in.localshop.os.catalog.CategoryView;
import in.localshop.os.catalog.ProductCatalogService;
import in.localshop.os.catalog.ProductList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/marketplace")
public class MarketplaceCatalogController {

    private final ProductCatalogService productCatalogService;

    public MarketplaceCatalogController(ProductCatalogService productCatalogService) {
        this.productCatalogService = productCatalogService;
    }

    @GetMapping("/categories")
    public List<CategoryView> categories() {
        return productCatalogService.activeCategories();
    }

    @GetMapping("/shops/{slug}/products")
    public ProductList products(
            @PathVariable String slug,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "50") int limit) {
        return productCatalogService.publicProducts(slug, category, limit);
    }
}
