package in.localshop.os.identity;

import java.util.UUID;

public record IdentityResolution(UUID userId, String nextAction) { }
