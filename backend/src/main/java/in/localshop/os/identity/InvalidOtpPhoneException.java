package in.localshop.os.identity;

public class InvalidOtpPhoneException extends RuntimeException {
    public InvalidOtpPhoneException() {
        super("Phone number is invalid.");
    }
}
