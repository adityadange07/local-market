package in.localshop.os.identity;

import java.util.List;

public record AdminMfaEnrollmentCompleteResponse(
        AdminMfaTokenResponse stepUpSession,
        List<String> recoveryCodes) { }
