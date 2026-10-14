package cn.slainc.mfa.totp;

import java.io.ByteArrayOutputStream;

public final class Base32Codec {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private static final int[] REVERSE = new int[128];

    static {
        for (int i = 0; i < REVERSE.length; i++) {
            REVERSE[i] = -1;
        }
        for (int i = 0; i < ALPHABET.length(); i++) {
            REVERSE[ALPHABET.charAt(i)] = i;
        }
    }

    private Base32Codec() {
    }

    public static String encode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte item : data) {
            buffer = (buffer << 8) | (item & 0xFF);
            bits += 8;
            while (bits >= 5) {
                builder.append(ALPHABET.charAt((buffer >>> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            builder.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return builder.toString();
    }

    public static byte[] decode(String text) {
        if (text == null || text.isEmpty()) {
            return new byte[0];
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(text.length() * 5 / 8 + 1);
        long buffer = 0;
        int bits = 0;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == ' ' || current == '\n' || current == '\r' || current == '\t' || current == '-' || current == '=') {
                continue;
            }
            char upper = Character.toUpperCase(current);
            if (upper >= 128 || REVERSE[upper] < 0) {
                throw new IllegalArgumentException("非法的 Base32 字符：" + current);
            }
            buffer = (buffer << 5) | REVERSE[upper];
            bits += 5;
            if (bits >= 8) {
                output.write((int) ((buffer >>> (bits - 8)) & 0xFF));
                bits -= 8;
            }
        }
        // 末尾不足 8 位的余位按标准必须为 0，此处宽容丢弃
        return output.toByteArray();
    }
}
