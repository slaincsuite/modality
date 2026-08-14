package cn.slainc.crypto.hash;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.codec.HexUtils;
import cn.slainc.crypto.random.SecureRandomUtils;
import cn.slainc.crypto.util.ConstantTimeUtils;
import java.nio.charset.StandardCharsets;
import org.bouncycastle.crypto.generators.SCrypt;

public final class ScryptUtils {

    public static final int DEFAULT_SALT_LENGTH = 16;

    private ScryptUtils() {
    }

    public static byte[] derive(byte[] password, byte[] salt, ScryptParams params) {
        if (password == null || salt == null) {
            throw new CryptoException("口令与盐值不能为空");
        }
        if (params.cost() < 2 || Integer.bitCount(params.cost()) != 1) {
            throw new CryptoException("scrypt 代价参数必须是大于 1 的 2 的幂");
        }
        return SCrypt.generate(password, salt, params.cost(), params.blockSize(), params.parallelization(), params.keyLength());
    }

    public static byte[] derive(String password, String salt, ScryptParams params) {
        return derive(bytes(password), bytes(salt), params);
    }

    public static String deriveHex(byte[] password, byte[] salt, ScryptParams params) {
        return HexUtils.encode(derive(password, salt, params));
    }

    public static String hashEncoded(byte[] password, ScryptParams params) {
        byte[] salt = SecureRandomUtils.nextBytes(DEFAULT_SALT_LENGTH);
        return encode(password, salt, params);
    }

    public static String hashEncoded(String password, ScryptParams params) {
        return hashEncoded(bytes(password), params);
    }

    public static String encode(byte[] password, byte[] salt, ScryptParams params) {
        byte[] hash = derive(password, salt, params);
        return "scrypt$" + params.cost() + "$" + params.blockSize() + "$" + params.parallelization()
                + "$" + Base64Utils.encode(salt).replace("=", "")
                + "$" + Base64Utils.encode(hash).replace("=", "");
    }

    public static boolean verify(byte[] password, String encoded) {
        if (encoded == null) {
            return false;
        }
        String[] parts = encoded.split("\\$");
        if (parts.length != 6 || !"scrypt".equals(parts[0])) {
            throw new CryptoException("非法的 scrypt 编码哈希");
        }
        int cost = Integer.parseInt(parts[1]);
        int blockSize = Integer.parseInt(parts[2]);
        int parallelization = Integer.parseInt(parts[3]);
        byte[] salt = Base64Utils.decode(pad(parts[4]));
        byte[] expected = Base64Utils.decode(pad(parts[5]));
        byte[] actual = derive(password, salt, ScryptParams.of(cost, blockSize, parallelization, expected.length));
        return ConstantTimeUtils.equals(actual, expected);
    }

    public static boolean verify(String password, String encoded) {
        return password != null && verify(bytes(password), encoded);
    }

    private static byte[] bytes(String text) {
        if (text == null) {
            throw new CryptoException("值不能为空");
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String pad(String text) {
        int remainder = text.length() & 3;
        return remainder == 0 ? text : text + "=".repeat(4 - remainder);
    }
}
