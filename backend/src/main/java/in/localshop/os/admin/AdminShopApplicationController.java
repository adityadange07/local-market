package in.localshop.os.admin;

import in.localshop.os.shops.RejectShopApplicationRequest;
import in.localshop.os.shops.ShopApplicationList;
import in.localshop.os.shops.ShopApplicationService;
import in.localshop.os.shops.ShopApplicationView;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/shop-applications")
public class AdminShopApplicationController {

    private final ShopApplicationService shopApplicationService;

    public AdminShopApplicationController(ShopApplicationService shopApplicationService) {
        this.shopApplicationService = shopApplicationService;
    }

    @GetMapping
    public ShopApplicationList pending(@RequestParam(defaultValue = "50") int limit) {
        return shopApplicationService.pendingApplications(limit);
    }

    @PostMapping("/{tenantId}/approve")
    public ShopApplicationView approve(@PathVariable UUID tenantId) {
        return shopApplicationService.approve(tenantId);
    }

    @PostMapping("/{tenantId}/reject")
    public ShopApplicationView reject(
            @PathVariable UUID tenantId,
            @Valid @RequestBody RejectShopApplicationRequest request) {
        return shopApplicationService.reject(tenantId, request.reason());
    }
}
