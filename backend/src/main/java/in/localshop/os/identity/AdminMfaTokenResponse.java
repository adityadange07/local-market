package in.localshop.os.identity;

public record AdminMfaTokenResponse(String accessToken, String tokenType, long expiresIn) { }
