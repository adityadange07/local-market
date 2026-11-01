package in.localshop.os.identity;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OtpSecurityConfiguration {

    @Bean
    OtpCodeHasher otpCodeHasher(@Value("${localshop.identity.otp.hmac-secret}") String encodedSecret) {
        try {
            return new OtpCodeHasher(Base64.getDecoder().decode(encodedSecret));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("OTP_HMAC_SECRET must be valid Base64 encoding of at least 32 random bytes.", exception);
        }
    }
}
