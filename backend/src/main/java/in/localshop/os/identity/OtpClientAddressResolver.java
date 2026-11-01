package in.localshop.os.identity;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

public class OtpClientAddressResolver {

    private final Set<String> trustedProxyAddresses;

    public OtpClientAddressResolver(Set<String> trustedProxyAddresses) {
        this.trustedProxyAddresses = trustedProxyAddresses.stream()
                .map(OtpClientAddressResolver::canonicalIp)
                .collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = canonicalIp(request.getRemoteAddr());
        if (!trustedProxyAddresses.contains(remoteAddress)) {
            return remoteAddress;
        }
        String forwardedAddress = request.getHeader("X-Real-IP");
        if (forwardedAddress == null || forwardedAddress.isBlank()) {
            return remoteAddress;
        }
        try {
            return canonicalIp(forwardedAddress.trim());
        } catch (IllegalArgumentException invalidHeader) {
            return remoteAddress;
        }
    }

    private static String canonicalIp(String value) {
        if (value == null || value.isBlank() || !value.matches("[0-9a-fA-F:.%]+")) {
            throw new IllegalArgumentException("Invalid client IP address.");
        }
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Invalid client IP address.", exception);
        }
    }
}
