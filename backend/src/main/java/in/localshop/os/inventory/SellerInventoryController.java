package in.localshop.os.inventory;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/tenants/{tenantId}")
public class SellerInventoryController {
    private final VariantInventoryService inventoryService;

    public SellerInventoryController(VariantInventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/products/{productId}/variants")
    public List<VariantView> variants(@PathVariable UUID tenantId, @PathVariable UUID productId) {
        return inventoryService.variants(tenantId, productId);
    }

    @PostMapping("/products/{productId}/variants")
    public VariantView createVariant(@PathVariable UUID tenantId, @PathVariable UUID productId,
            @Valid @RequestBody VariantRequest request) {
        return inventoryService.createVariant(tenantId, productId, request);
    }

    @GetMapping("/variants/{variantId}/inventory")
    public InventoryView inventory(@PathVariable UUID tenantId, @PathVariable UUID variantId) {
        return inventoryService.inventory(tenantId, variantId);
    }

    @PostMapping("/variants/{variantId}/inventory/adjustments")
    public InventoryView adjust(@PathVariable UUID tenantId, @PathVariable UUID variantId,
            @Valid @RequestBody StockAdjustmentRequest request) {
        return inventoryService.adjust(tenantId, variantId, request);
    }

    @GetMapping("/variants/{variantId}/inventory/movements")
    public List<StockMovementView> movements(@PathVariable UUID tenantId, @PathVariable UUID variantId,
            @RequestParam(defaultValue = "50") int limit) {
        return inventoryService.movements(tenantId, variantId, limit);
    }
}
