package in.localshop.os.identity;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OtpApplicationConfiguration {

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    SecureRandom otpSecureRandom() {
        return new SecureRandom();
    }

    @Bean
    OtpClientAddressResolver otpClientAddressResolver(
            @Value("${localshop.identity.otp.trusted-proxy-addresses:}") String trustedProxyAddresses) {
        Set<String> trusted = Arrays.stream(trustedProxyAddresses.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        return new OtpClientAddressResolver(trusted);
    }

    @Bean
    @ConditionalOnBean(SmsProvider.class)
    OtpChallengeService otpChallengeService(
            OtpChallengeRepository repository,
            SmsProvider smsProvider,
            OtpCodeHasher hasher,
            SecureRandom secureRandom,
            Clock clock,
            @Value("${localshop.identity.otp.lifetime:5m}") Duration lifetime,
            @Value("${localshop.identity.otp.max-attempts:5}") int maxAttempts) {
        return new OtpChallengeService(repository, smsProvider, hasher, secureRandom, clock, lifetime, maxAttempts);
    }
}
