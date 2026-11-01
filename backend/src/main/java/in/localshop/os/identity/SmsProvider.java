package in.localshop.os.identity;

/** Vendor boundary for SMS delivery. Implementations must never log or persist the OTP in plaintext. */
public interface SmsProvider {

    void sendOtp(String phoneE164, OtpPurpose purpose, String code);
}
