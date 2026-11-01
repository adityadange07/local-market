package in.localshop.os.identity;

public enum OtpVerificationResult {
    VERIFIED,
    INVALID,
    EXPIRED,
    LOCKED,
    ALREADY_USED,
    NOT_DELIVERED,
    NOT_FOUND
}
