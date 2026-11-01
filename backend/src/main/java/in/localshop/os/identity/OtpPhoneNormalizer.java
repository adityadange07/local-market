package in.localshop.os.identity;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class OtpPhoneNormalizer {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{1,14}$");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s().-]");
    private static final Pattern DIGITS = Pattern.compile("^[0-9]+$");

    public String normalize(String input) {
        if (input == null || input.isBlank() || input.length() > 32) {
            throw new InvalidOtpPhoneException();
        }
        String compact = SEPARATORS.matcher(input.trim()).replaceAll("");
        if (compact.startsWith("00")) {
            compact = "+" + compact.substring(2);
        }
        if (compact.startsWith("+")) {
            if (!E164.matcher(compact).matches()) {
                throw new InvalidOtpPhoneException();
            }
            return compact;
        }
        if (!DIGITS.matcher(compact).matches()) {
            throw new InvalidOtpPhoneException();
        }
        if (compact.length() == 10) {
            return "+91" + compact;
        }
        if (compact.length() == 11 && compact.startsWith("0")) {
            return "+91" + compact.substring(1);
        }
        if (compact.length() == 12 && compact.startsWith("91")) {
            return "+" + compact;
        }
        throw new InvalidOtpPhoneException();
    }
}
