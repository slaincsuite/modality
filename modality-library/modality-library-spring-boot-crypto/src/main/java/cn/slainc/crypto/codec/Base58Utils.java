package cn.slainc.crypto.codec;

import cn.slainc.crypto.CryptoException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public final class Base58Utils {

    private static final char[] ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

    private static final int[] INDEXES = new int[128];

    static {
        Arrays.fill(INDEXES, -1);
        for (int i = 0; i < ALPHABET.length; i++) {
            INDEXES[ALPHABET[i]] = i;
        }
    }

    private Base58Utils() {
    }

    public static String encode(byte[] input) {
        if (input == null) {
            return null;
        }
        if (input.length == 0) {
            return "";
        }
        byte[] number = input.clone();
        int zeros = 0;
        while (zeros < number.length && number[zeros] == 0) {
            zeros++;
        }
        char[] encoded = new char[number.length * 2];
        int outputStart = encoded.length;
        for (int inputStart = zeros; inputStart < number.length; ) {
            encoded[--outputStart] = ALPHABET[divmod(number, inputStart, 256, 58)];
            if (number[inputStart] == 0) {
                inputStart++;
            }
        }
        while (outputStart < encoded.length && encoded[outputStart] == ALPHABET[0]) {
            outputStart++;
        }
        while (--zeros >= 0) {
            encoded[--outputStart] = ALPHABET[0];
        }
        return new String(encoded, outputStart, encoded.length - outputStart);
    }

    public static byte[] decode(String input) {
        if (input == null) {
            return null;
        }
        if (input.isEmpty()) {
            return new byte[0];
        }
        byte[] number = new byte[input.length()];
        for (int i = 0; i < input.length(); i++) {
            char character = input.charAt(i);
            int digit = character < 128 ? INDEXES[character] : -1;
            if (digit < 0) {
                throw new CryptoException("非法的 base58 字符：" + character);
            }
            number[i] = (byte) digit;
        }
        int zeros = 0;
        while (zeros < number.length && number[zeros] == 0) {
            zeros++;
        }
        byte[] decoded = new byte[number.length];
        int outputStart = decoded.length;
        for (int inputStart = zeros; inputStart < number.length; ) {
            decoded[--outputStart] = (byte) divmod(number, inputStart, 58, 256);
            if (number[inputStart] == 0) {
                inputStart++;
            }
        }
        while (outputStart < decoded.length && decoded[outputStart] == 0) {
            outputStart++;
        }
        return Arrays.copyOfRange(decoded, outputStart - zeros, decoded.length);
    }

    public static String encodeChecked(byte[] input) {
        byte[] checksum = checksum(input);
        byte[] payload = Arrays.copyOf(input, input.length + checksum.length);
        System.arraycopy(checksum, 0, payload, input.length, checksum.length);
        return encode(payload);
    }

    public static byte[] decodeChecked(String input) {
        byte[] decoded = decode(input);
        if (decoded.length < 4) {
            throw new CryptoException("Base58Check 数据长度不足");
        }
        int bodyLength = decoded.length - 4;
        byte[] body = Arrays.copyOf(decoded, bodyLength);
        byte[] expected = Arrays.copyOfRange(decoded, bodyLength, decoded.length);
        if (!MessageDigest.isEqual(checksum(body), expected)) {
            throw new CryptoException("Base58Check 校验和不匹配");
        }
        return body;
    }

    private static byte[] checksum(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Arrays.copyOf(digest.digest(digest.digest(input)), 4);
        } catch (NoSuchAlgorithmException exception) {
            throw new CryptoException("SHA-256 算法不可用", exception);
        }
    }

    private static int divmod(byte[] number, int firstDigit, int base, int divisor) {
        int remainder = 0;
        for (int i = firstDigit; i < number.length; i++) {
            int digit = number[i] & 0xFF;
            int temp = remainder * base + digit;
            number[i] = (byte) (temp / divisor);
            remainder = temp % divisor;
        }
        return remainder;
    }
}
