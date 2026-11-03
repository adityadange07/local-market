package in.localshop.os.shops;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/applications")
public class SellerShopApplicationController {

    private final ShopApplicationService shopApplicationService;

    public SellerShopApplicationController(ShopApplicationService shopApplicationService) {
        this.shopApplicationService = shopApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShopApplicationView submit(@Valid @RequestBody ShopApplicationRequest request) {
        return shopApplicationService.submit(request);
    }

    @GetMapping
    public ShopApplicationList mine(@RequestParam(defaultValue = "20") int limit) {
        return shopApplicationService.ownApplications(limit);
    }
}
