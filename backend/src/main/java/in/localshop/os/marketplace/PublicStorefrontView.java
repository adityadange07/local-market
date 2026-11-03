package in.localshop.os.marketplace;

import java.util.List;

import in.localshop.os.shops.StoreAddressView;
import in.localshop.os.shops.StoreFulfillmentView;
import in.localshop.os.shops.StoreHourView;

public record PublicStorefrontView(
        String displayName,
        String description,
        String slug,
        StoreAddressView address,
        StoreFulfillmentView fulfillment,
        List<StoreHourView> hours) { }
