package in.localshop.os.shops;

public record StoreAddressView(
        String addressLine1,
        String addressLine2,
        String landmark,
        String locality,
        String city,
        String state,
        String postalCode,
        double latitude,
        double longitude) { }
