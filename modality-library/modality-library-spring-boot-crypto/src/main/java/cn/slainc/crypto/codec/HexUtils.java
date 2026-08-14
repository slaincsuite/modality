package cn.slainc.crypto.codec;

import cn.slainc.crypto.CryptoException;

public final class HexUtils {

    private static final char[] LOWER_DIGITS = "0123456789abcdef".toCharArray();

    private static final char[] UPPER_DIGITS = "0123456789ABCDEF".toCharArray();

    private HexUtils() {
    }

    public static String encode(byte[] bytes) {
        return encode(bytes, false);
    }

    public static String encodeUpper(byte[] bytes) {
        return encode(bytes, true);
    }

    public static String encode(byte[] bytes, boolean upper) {
        if (bytes == null) {
            return null;
        }
        char[] digits = upper ? UPPER_DIGITS : LOWER_DIGITS;
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            chars[i * 2] = digits[value >>> 4];
            chars[i * 2 + 1] = digits[value & 0x0F];
        }
        return new String(chars);
    }

    public static byte[] decode(String hex) {
        if (hex == null) {
            return null;
        }
        String text = stripPrefix(hex.trim());
        if ((text.length() & 1) != 0) {
            throw new CryptoException("十六进制文本长度必须为偶数");
        }
        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = digit(text.charAt(i * 2));
            int low = digit(text.charAt(i * 2 + 1));
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }

    public static boolean isHex(String text) {
        if (text == null) {
            return false;
        }
        String value = stripPrefix(text.trim());
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String stripPrefix(String text) {
        if (text.startsWith("0x") || text.startsWith("0X")) {
            return text.substring(2);
        }
        return text;
    }

    private static int digit(char character) {
        int value = Character.digit(character, 16);
        if (value < 0) {
            throw new CryptoException("非法的十六进制字符：" + character);
        }
        return value;
    }
}
