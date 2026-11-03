package in.localshop.os.catalog;

import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/tenants/{tenantId}/products")
public class SellerProductController {

    private final ProductCatalogService productCatalogService;

    public SellerProductController(ProductCatalogService productCatalogService) {
        this.productCatalogService = productCatalogService;
    }

    @GetMapping
    public ProductList list(@PathVariable UUID tenantId, @RequestParam(defaultValue = "50") int limit) {
        return productCatalogService.sellerProducts(tenantId, limit);
    }

    @PostMapping
    public ProductView create(@PathVariable UUID tenantId, @Valid @RequestBody ProductRequest request) {
        return productCatalogService.create(tenantId, request);
    }

    @PutMapping("/{productId}")
    public ProductView update(
            @PathVariable UUID tenantId,
            @PathVariable UUID productId,
            @Valid @RequestBody ProductRequest request) {
        return productCatalogService.update(tenantId, productId, request);
    }

    @PostMapping("/{productId}/publish")
    public ProductView publish(@PathVariable UUID tenantId, @PathVariable UUID productId) {
        return productCatalogService.publish(tenantId, productId);
    }

    @PostMapping("/{productId}/archive")
    public ProductView archive(@PathVariable UUID tenantId, @PathVariable UUID productId) {
        return productCatalogService.archive(tenantId, productId);
    }
}
