package in.localshop.os.identity;

public class IdentityAuthenticationRejectedException extends RuntimeException {
    public IdentityAuthenticationRejectedException() {
        super("OTP verification could not be completed.");
    }
}
