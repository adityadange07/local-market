package in.localshop.os.shops;

import java.util.List;
import java.util.UUID;

public record SellerStoreView(
        UUID storeId,
        String displayName,
        String description,
        String slug,
        String status,
        StoreAddressView address,
        StoreFulfillmentView fulfillment,
        List<StoreHourView> hours) { }
