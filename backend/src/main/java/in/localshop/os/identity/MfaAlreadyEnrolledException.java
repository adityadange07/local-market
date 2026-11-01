package in.localshop.os.identity;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class MfaAlreadyEnrolledException extends ResponseStatusException {
    public MfaAlreadyEnrolledException() {
        super(HttpStatus.CONFLICT, "Admin MFA is already enrolled.");
    }
}
