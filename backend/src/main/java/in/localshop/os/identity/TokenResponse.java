package in.localshop.os.identity;

import java.time.Instant;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        Instant refreshExpiresAt) { }
