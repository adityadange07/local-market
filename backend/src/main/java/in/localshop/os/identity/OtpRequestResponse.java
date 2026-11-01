package in.localshop.os.identity;

import java.util.UUID;

public record OtpRequestResponse(UUID challengeId, String message) { }
