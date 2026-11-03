package in.localshop.os.shops;

public record StoreFulfillmentView(
        boolean deliveryEnabled,
        boolean pickupEnabled,
        Integer deliveryRadiusMeters,
        long minimumOrderMinor,
        String timezone) { }
