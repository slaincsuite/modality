package cn.slainc.crypto.hash;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.CryptoProviders;
import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.codec.HexUtils;
import cn.slainc.crypto.random.SecureRandomUtils;
import cn.slainc.crypto.util.ConstantTimeUtils;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Pbkdf2Utils {

    public static final int DEFAULT_ITERATIONS = 210000;

    public static final int DEFAULT_SALT_LENGTH = 16;

    public static final int DEFAULT_KEY_LENGTH = 32;

    private Pbkdf2Utils() {
    }

    public static byte[] derive(Pbkdf2Algorithm algorithm, char[] password, byte[] salt, int iterations, int keyLength) {
        if (password == null || salt == null || salt.length == 0) {
            throw new CryptoException("口令与盐值不能为空");
        }
        if (iterations < 1) {
            throw new CryptoException("迭代次数必须为正数");
        }
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength * 8);
        try {
            return SecretKeyFactory.getInstance(algorithm.jcaName(), CryptoProviders.bouncyCastle()).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("密钥派生失败，算法：" + algorithm, exception);
        } finally {
            spec.clearPassword();
        }
    }

    public static byte[] derive(Pbkdf2Algorithm algorithm, byte[] password, byte[] salt, int iterations, int keyLength) {
        char[] chars = new String(password == null ? new byte[0] : password, java.nio.charset.StandardCharsets.UTF_8).toCharArray();
        try {
            return derive(algorithm, chars, salt, iterations, keyLength);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    public static String deriveHex(Pbkdf2Algorithm algorithm, char[] password, byte[] salt, int iterations, int keyLength) {
        return HexUtils.encode(derive(algorithm, password, salt, iterations, keyLength));
    }

    public static String hashEncoded(Pbkdf2Algorithm algorithm, char[] password) {
        return hashEncoded(algorithm, password, DEFAULT_ITERATIONS, DEFAULT_KEY_LENGTH);
    }

    public static String hashEncoded(Pbkdf2Algorithm algorithm, char[] password, int iterations, int keyLength) {
        byte[] salt = SecureRandomUtils.nextBytes(DEFAULT_SALT_LENGTH);
        byte[] hash = derive(algorithm, password, salt, iterations, keyLength);
        return algorithm.label() + "$" + iterations + "$" + Base64Utils.encode(salt).replace("=", "") + "$" + Base64Utils.encode(hash).replace("=", "");
    }

    public static boolean verify(char[] password, String encoded) {
        if (encoded == null) {
            return false;
        }
        String[] parts = encoded.split("\\$");
        if (parts.length != 4) {
            throw new CryptoException("非法的 PBKDF2 编码哈希");
        }
        Pbkdf2Algorithm algorithm = Pbkdf2Algorithm.fromLabel(parts[0]);
        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = Base64Utils.decode(pad(parts[2]));
        byte[] expected = Base64Utils.decode(pad(parts[3]));
        byte[] actual = derive(algorithm, password, salt, iterations, expected.length);
        return ConstantTimeUtils.equals(actual, expected);
    }

    public static boolean verify(String password, String encoded) {
        return password != null && verify(password.toCharArray(), encoded);
    }

    private static String pad(String text) {
        int remainder = text.length() & 3;
        return remainder == 0 ? text : text + "=".repeat(4 - remainder);
    }
}
