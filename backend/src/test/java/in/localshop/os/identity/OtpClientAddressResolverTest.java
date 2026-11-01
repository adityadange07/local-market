package in.localshop.os.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class OtpClientAddressResolverTest {

    @Test
    void ignoresForwardedAddressFromUntrustedPeer() {
        OtpClientAddressResolver resolver = new OtpClientAddressResolver(Set.of());
        MockHttpServletRequest request = request("192.0.2.4");
        request.addHeader("X-Real-IP", "198.51.100.9");

        assertThat(resolver.resolve(request)).isEqualTo("192.0.2.4");
    }

    @Test
    void acceptsRealAddressOnlyFromConfiguredProxyAndIgnoresMalformedHeader() {
        OtpClientAddressResolver resolver = new OtpClientAddressResolver(Set.of("127.0.0.1"));
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.9");
        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.9");

        request.removeHeader("X-Real-IP");
        request.addHeader("X-Real-IP", "attacker.example");
        assertThat(resolver.resolve(request)).isEqualTo("127.0.0.1");
    }

    private static MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
