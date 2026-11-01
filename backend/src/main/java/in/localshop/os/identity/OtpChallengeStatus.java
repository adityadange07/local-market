package in.localshop.os.identity;

public enum OtpChallengeStatus {
    PENDING,
    SENT,
    DELIVERY_FAILED,
    CONSUMED,
    EXPIRED,
    LOCKED
}
