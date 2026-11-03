package in.localshop.os.customer;

import java.time.Instant;
import java.util.UUID;

public record CustomerAddressView(UUID id, String label, String recipientName, String recipientPhone,
        String addressLine1, String addressLine2, String landmark, String locality, String city,
        String state, String postalCode, Double latitude, Double longitude, boolean defaultAddress,
        Instant updatedAt) { }
