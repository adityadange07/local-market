package in.localshop.os.marketplace;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/marketplace/shops/{shopSlug}/products/{productSlug}/variants")
public class PublicVariantController {
    private final MarketplaceVariantService variants;

    public PublicVariantController(MarketplaceVariantService variants) { this.variants = variants; }

    @GetMapping
    public List<PublicVariantView> list(@PathVariable String shopSlug, @PathVariable String productSlug) {
        return variants.variants(shopSlug, productSlug);
    }
}
