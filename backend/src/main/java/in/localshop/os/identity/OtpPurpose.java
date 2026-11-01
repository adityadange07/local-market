package in.localshop.os.identity;

/** Authentication action that an OTP is permitted to authorize. */
public enum OtpPurpose {
    CUSTOMER_LOGIN,
    SELLER_LOGIN,
    SELLER_REGISTRATION
}
