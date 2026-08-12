package cn.slainc.utils.string;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

public final class StringUtils {

    public static final String EMPTY = "";

    private StringUtils() {
    }

    public static boolean isEmpty(CharSequence text) {
        return text == null || text.length() == 0;
    }

    public static boolean isNotEmpty(CharSequence text) {
        return !isEmpty(text);
    }

    public static boolean isBlank(CharSequence text) {
        if (text == null) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNotBlank(CharSequence text) {
        return !isBlank(text);
    }

    public static boolean isAnyBlank(CharSequence... texts) {
        if (texts == null || texts.length == 0) {
            return true;
        }
        for (CharSequence text : texts) {
            if (isBlank(text)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAllBlank(CharSequence... texts) {
        if (texts == null || texts.length == 0) {
            return true;
        }
        for (CharSequence text : texts) {
            if (isNotBlank(text)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNumeric(CharSequence text) {
        if (isEmpty(text)) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static String trim(String text) {
        return text == null ? null : text.trim();
    }

    public static String trimToNull(String text) {
        String trimmed = trim(text);
        return isBlank(trimmed) ? null : trimmed;
    }

    public static String trimToEmpty(String text) {
        return text == null ? EMPTY : text.trim();
    }

    public static String nullToEmpty(String text) {
        return text == null ? EMPTY : text;
    }

    public static String emptyToNull(String text) {
        return isEmpty(text) ? null : text;
    }

    public static String defaultIfBlank(String text, String defaultValue) {
        return isBlank(text) ? defaultValue : text;
    }

    public static String defaultIfEmpty(String text, String defaultValue) {
        return isEmpty(text) ? defaultValue : text;
    }

    public static String upperCase(String text) {
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    public static String lowerCase(String text) {
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    public static String capitalize(String text) {
        if (isEmpty(text)) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    public static String uncapitalize(String text) {
        if (isEmpty(text)) {
            return text;
        }
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    public static String repeat(String text, int times) {
        if (text == null || times <= 0) {
            return EMPTY;
        }
        return text.repeat(times);
    }

    public static String truncate(String text, int maxLength) {
        return truncate(text, maxLength, EMPTY);
    }

    public static String truncate(String text, int maxLength, String suffix) {
        if (text == null || maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        String actualSuffix = nullToEmpty(suffix);
        int keep = Math.max(0, maxLength - actualSuffix.length());
        return text.substring(0, keep) + actualSuffix;
    }

    public static String leftPad(String text, int size, char padChar) {
        if (text == null || text.length() >= size) {
            return text;
        }
        return String.valueOf(padChar).repeat(size - text.length()) + text;
    }

    public static String rightPad(String text, int size, char padChar) {
        if (text == null || text.length() >= size) {
            return text;
        }
        return text + String.valueOf(padChar).repeat(size - text.length());
    }

    public static String mask(String text, int prefixKeep, int suffixKeep) {
        if (isEmpty(text)) {
            return text;
        }
        int length = text.length();
        if (prefixKeep < 0 || suffixKeep < 0 || prefixKeep + suffixKeep >= length) {
            return "*".repeat(length);
        }
        return text.substring(0, prefixKeep)
                + "*".repeat(length - prefixKeep - suffixKeep)
                + text.substring(length - suffixKeep);
    }

    public static String maskMobile(String mobile) {
        return mask(mobile, 3, 4);
    }

    public static String maskEmail(String email) {
        if (isEmpty(email)) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return mask(email, 1, 0);
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        return mask(local, Math.min(1, local.length()), 0) + domain;
    }

    public static String maskIdCard(String idCard) {
        return mask(idCard, 6, 4);
    }

    public static String maskBankCard(String bankCard) {
        return mask(bankCard, 4, 4);
    }

    public static boolean equals(String left, String right) {
        return Objects.equals(left, right);
    }

    public static boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.equalsIgnoreCase(right);
    }

    public static boolean contains(String text, String search) {
        return text != null && search != null && text.contains(search);
    }

    public static boolean containsIgnoreCase(String text, String search) {
        if (text == null || search == null) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }

    public static boolean startsWith(String text, String prefix) {
        return text != null && prefix != null && text.startsWith(prefix);
    }

    public static boolean endsWith(String text, String suffix) {
        return text != null && suffix != null && text.endsWith(suffix);
    }

    public static boolean startsWithIgnoreCase(String text, String prefix) {
        if (text == null || prefix == null) {
            return false;
        }
        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    public static boolean endsWithIgnoreCase(String text, String suffix) {
        if (text == null || suffix == null) {
            return false;
        }
        int offset = text.length() - suffix.length();
        return offset >= 0 && text.regionMatches(true, offset, suffix, 0, suffix.length());
    }

    public static String removePrefix(String text, String prefix) {
        if (text == null || prefix == null || !text.startsWith(prefix)) {
            return text;
        }
        return text.substring(prefix.length());
    }

    public static String removeSuffix(String text, String suffix) {
        if (text == null || suffix == null || !text.endsWith(suffix)) {
            return text;
        }
        return text.substring(0, text.length() - suffix.length());
    }

    public static int countMatches(String text, String search) {
        if (isEmpty(text) || isEmpty(search)) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(search, index)) >= 0) {
            count++;
            index += search.length();
        }
        return count;
    }

    public static String substringBefore(String text, String separator) {
        if (isEmpty(text) || separator == null) {
            return text;
        }
        int index = text.indexOf(separator);
        return index < 0 ? text : text.substring(0, index);
    }

    public static String substringAfter(String text, String separator) {
        if (isEmpty(text) || separator == null) {
            return text;
        }
        int index = text.indexOf(separator);
        return index < 0 ? EMPTY : text.substring(index + separator.length());
    }

    public static String substringBetween(String text, String open, String close) {
        if (text == null || open == null || close == null) {
            return null;
        }
        int start = text.indexOf(open);
        if (start < 0) {
            return null;
        }
        int from = start + open.length();
        int end = text.indexOf(close, from);
        return end < 0 ? null : text.substring(from, end);
    }

    public static String join(Collection<?> items, String separator) {
        if (items == null || items.isEmpty()) {
            return EMPTY;
        }
        String actualSeparator = nullToEmpty(separator);
        StringBuilder builder = new StringBuilder();
        for (Object item : items) {
            if (builder.length() > 0) {
                builder.append(actualSeparator);
            }
            builder.append(item);
        }
        return builder.toString();
    }

    public static String join(Object[] items, String separator) {
        return items == null ? EMPTY : join(java.util.Arrays.asList(items), separator);
    }

    public static String abbreviate(String text, int maxLength) {
        return truncate(text, maxLength, "...");
    }
}
