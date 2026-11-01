package in.localshop.os.identity;

public record OtpVerifyResponse(boolean verified, String nextAction, TokenResponse tokens, String message) { }
