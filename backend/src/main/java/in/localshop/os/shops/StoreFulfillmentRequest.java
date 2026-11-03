package in.localshop.os.shops;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record StoreFulfillmentRequest(
        boolean deliveryEnabled,
        boolean pickupEnabled,
        Integer deliveryRadiusMeters,
        @PositiveOrZero long minimumOrderMinor,
        @NotBlank @Size(max = 64) String timezone) { }
