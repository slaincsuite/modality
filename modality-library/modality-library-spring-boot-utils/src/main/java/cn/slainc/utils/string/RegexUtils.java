package cn.slainc.utils.string;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RegexUtils {

    public static final String MOBILE = "^1[3-9]\\d{9}$";
    public static final String EMAIL = "^[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}$";
    public static final String ID_CARD = "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$";
    public static final String URL = "^(https?|ftp)://[^\\s/$.?#][^\\s]*$";
    public static final String IPV4 = "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)$";
    public static final String IPV6 = "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|::([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4})$";
    public static final String BANK_CARD = "^\\d{16,19}$";
    public static final String CHINESE = "^[\\u4e00-\\u9fa5]+$";
    public static final String POSTAL_CODE = "^[1-9]\\d{5}$";
    public static final String USERNAME = "^[a-zA-Z][a-zA-Z0-9_]{3,31}$";
    public static final String PASSWORD_STRONG = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,64}$";
    public static final String HEX_COLOR = "^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$";
    public static final String MAC_ADDRESS = "^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$";
    public static final String CAR_PLATE = "^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领][A-HJ-NP-Z][A-HJ-NP-Z0-9]{4,5}[A-HJ-NP-Z0-9挂学警港澳]$";

    private RegexUtils() {
    }

    public static boolean isMobile(String text) {
        return matches(MOBILE, text);
    }

    public static boolean isEmail(String text) {
        return matches(EMAIL, text);
    }

    public static boolean isIdCard(String text) {
        return matches(ID_CARD, text);
    }

    public static boolean isUrl(String text) {
        return matches(URL, text);
    }

    public static boolean isIpv4(String text) {
        return matches(IPV4, text);
    }

    public static boolean isIpv6(String text) {
        return matches(IPV6, text);
    }

    public static boolean isIp(String text) {
        return isIpv4(text) || isIpv6(text);
    }

    public static boolean isBankCard(String text) {
        return matches(BANK_CARD, text);
    }

    public static boolean isChinese(String text) {
        return matches(CHINESE, text);
    }

    public static boolean isPostalCode(String text) {
        return matches(POSTAL_CODE, text);
    }

    public static boolean isUsername(String text) {
        return matches(USERNAME, text);
    }

    public static boolean isStrongPassword(String text) {
        return matches(PASSWORD_STRONG, text);
    }

    public static boolean isHexColor(String text) {
        return matches(HEX_COLOR, text);
    }

    public static boolean isMacAddress(String text) {
        return matches(MAC_ADDRESS, text);
    }

    public static boolean isCarPlate(String text) {
        return matches(CAR_PLATE, text);
    }

    public static boolean matches(String regex, String text) {
        if (text == null || regex == null) {
            return false;
        }
        return Pattern.matches(regex, text);
    }

    public static String extract(String regex, String text, int group) {
        if (regex == null || text == null) {
            return null;
        }
        Matcher matcher = Pattern.compile(regex).matcher(text);
        if (matcher.find() && matcher.groupCount() >= group) {
            return matcher.group(group);
        }
        return null;
    }

    public static String extract(String regex, String text) {
        return extract(regex, text, 0);
    }

    public static List<String> extractAll(String regex, String text, int group) {
        List<String> results = new ArrayList<>();
        if (regex == null || text == null) {
            return results;
        }
        Matcher matcher = Pattern.compile(regex).matcher(text);
        while (matcher.find()) {
            if (matcher.groupCount() >= group) {
                results.add(matcher.group(group));
            }
        }
        return results;
    }

    public static List<String> extractAll(String regex, String text) {
        return extractAll(regex, text, 0);
    }

    public static String replaceAll(String regex, String text, String replacement) {
        if (regex == null || text == null) {
            return text;
        }
        return Pattern.compile(regex).matcher(text).replaceAll(replacement);
    }

    public static boolean hasMatch(String regex, String text) {
        if (regex == null || text == null) {
            return false;
        }
        return Pattern.compile(regex).matcher(text).find();
    }
}
