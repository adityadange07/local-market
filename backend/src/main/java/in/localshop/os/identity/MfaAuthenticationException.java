package in.localshop.os.identity;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class MfaAuthenticationException extends ResponseStatusException {
    public MfaAuthenticationException() {
        super(HttpStatus.UNAUTHORIZED, "MFA verification failed.");
    }
}
