package in.localshop.os.marketplace;

import java.util.UUID;

public record PublicVariantView(UUID id, String variantCode, String name, Long priceMinor) { }
