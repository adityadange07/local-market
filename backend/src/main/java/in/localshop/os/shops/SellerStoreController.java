package in.localshop.os.shops;

import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/tenants/{tenantId}/store")
public class SellerStoreController {

    private final StoreProfileService storeProfileService;

    public SellerStoreController(StoreProfileService storeProfileService) {
        this.storeProfileService = storeProfileService;
    }

    @GetMapping
    public SellerStoreView get(@PathVariable UUID tenantId) {
        return storeProfileService.getSellerStore(tenantId);
    }

    @PutMapping
    public SellerStoreView updateProfile(
            @PathVariable UUID tenantId,
            @Valid @RequestBody StoreProfileRequest request) {
        return storeProfileService.updateProfile(tenantId, request);
    }

    @PutMapping("/address")
    public SellerStoreView updateAddress(
            @PathVariable UUID tenantId,
            @Valid @RequestBody StoreAddressRequest request) {
        return storeProfileService.updateAddress(tenantId, request);
    }

    @PutMapping("/fulfillment")
    public SellerStoreView updateFulfillment(
            @PathVariable UUID tenantId,
            @Valid @RequestBody StoreFulfillmentRequest request) {
        return storeProfileService.updateFulfillment(tenantId, request);
    }

    @PutMapping("/hours")
    public SellerStoreView updateHours(
            @PathVariable UUID tenantId,
            @Valid @RequestBody StoreHoursRequest request) {
        return storeProfileService.updateHours(tenantId, request);
    }

    @PostMapping("/publish")
    public SellerStoreView publish(@PathVariable UUID tenantId) {
        return storeProfileService.publish(tenantId);
    }
}
