package in.localshop.os.identity;

import java.time.Instant;

public record AdminMfaEnrollmentStartResponse(String secret, String otpauthUri, Instant expiresAt) { }
