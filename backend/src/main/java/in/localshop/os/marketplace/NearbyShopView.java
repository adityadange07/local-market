package in.localshop.os.marketplace;

import java.util.UUID;

public record NearbyShopView(UUID storeId, String slug, String name, String description,
        String locality, String city, String postalCode, boolean deliveryEnabled,
        boolean pickupEnabled, double distanceMeters) { }
