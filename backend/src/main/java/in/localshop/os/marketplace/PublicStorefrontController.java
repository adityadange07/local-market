package in.localshop.os.marketplace;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/marketplace/shops")
public class PublicStorefrontController {

    private final in.localshop.os.shops.StoreProfileService storeProfileService;

    public PublicStorefrontController(in.localshop.os.shops.StoreProfileService storeProfileService) {
        this.storeProfileService = storeProfileService;
    }

    @GetMapping("/{slug}")
    public PublicStorefrontView get(@PathVariable String slug) {
        return storeProfileService.publicStorefront(slug);
    }
}
