package cn.slainc.crypto.hash;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.random.SecureRandomUtils;
import cn.slainc.crypto.util.ConstantTimeUtils;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

public final class Argon2Utils {

    public static final int DEFAULT_SALT_LENGTH = 16;

    public static final int VERSION = Argon2Parameters.ARGON2_VERSION_13;

    private Argon2Utils() {
    }

    public static byte[] hash(Argon2Variant variant, Argon2Params params, char[] password, byte[] salt) {
        return hash(variant, params, toBytes(password), salt);
    }

    public static byte[] hash(Argon2Variant variant, Argon2Params params, byte[] password, byte[] salt) {
        if (password == null || password.length == 0 || salt == null || salt.length == 0) {
            throw new CryptoException("口令与盐值不能为空");
        }
        Argon2Parameters.Builder builder = new Argon2Parameters.Builder(variant.type())
                .withVersion(VERSION)
                .withMemoryAsKB(params.memoryKb())
                .withIterations(params.iterations())
                .withParallelism(params.parallelism())
                .withSalt(salt);
        if (params.secret() != null) {
            builder.withSecret(params.secret());
        }
        if (params.additional() != null) {
            builder.withAdditional(params.additional());
        }
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(builder.build());
        byte[] output = new byte[params.hashLength()];
        generator.generateBytes(password, output);
        return output;
    }

    public static String hashEncoded(Argon2Variant variant, Argon2Params params, char[] password) {
        byte[] salt = SecureRandomUtils.nextBytes(DEFAULT_SALT_LENGTH);
        byte[] hash = hash(variant, params, password, salt);
        return encode(variant, params, salt, hash);
    }

    public static String hashEncoded(Argon2Variant variant, char[] password) {
        return hashEncoded(variant, Argon2Params.defaults(), password);
    }

    public static boolean verify(char[] password, String encoded) {
        if (password == null || encoded == null) {
            return false;
        }
        Parsed parsed = parse(encoded);
        byte[] hash = hash(parsed.variant, parsed.params, toBytes(password), parsed.salt);
        return ConstantTimeUtils.equals(hash, parsed.hash);
    }

    public static boolean verify(String password, String encoded) {
        return verify(password == null ? null : password.toCharArray(), encoded);
    }

    public static String encode(Argon2Variant variant, Argon2Params params, byte[] salt, byte[] hash) {
        return "$" + variant.label() + "$v=" + VERSION
                + "$m=" + params.memoryKb() + ",t=" + params.iterations() + ",p=" + params.parallelism()
                + "$" + Base64Utils.encode(salt).replace("=", "")
                + "$" + Base64Utils.encode(hash).replace("=", "");
    }

    public static boolean isArgon2(String encoded) {
        return encoded != null && encoded.startsWith("$argon2");
    }

    private static Parsed parse(String encoded) {
        if (!isArgon2(encoded)) {
            throw new CryptoException("不是 Argon2 编码的哈希");
        }
        String[] parts = encoded.split("\\$");
        if (parts.length != 6) {
            throw new CryptoException("非法的 Argon2 编码哈希");
        }
        Argon2Variant variant = Argon2Variant.fromLabel(parts[1]);
        if (!parts[2].startsWith("v=")) {
            throw new CryptoException("非法的 Argon2 版本段");
        }
        int memoryKb = 0;
        int iterations = 0;
        int parallelism = 0;
        for (String segment : parts[3].split(",")) {
            String[] pair = segment.split("=");
            if (pair.length != 2) {
                throw new CryptoException("非法的 Argon2 参数段");
            }
            switch (pair[0]) {
                case "m" -> memoryKb = Integer.parseInt(pair[1]);
                case "t" -> iterations = Integer.parseInt(pair[1]);
                case "p" -> parallelism = Integer.parseInt(pair[1]);
                default -> throw new CryptoException("未知的 Argon2 参数：" + pair[0]);
            }
        }
        if (memoryKb <= 0 || iterations <= 0 || parallelism <= 0) {
            throw new CryptoException("非法的 Argon2 参数");
        }
        byte[] salt = Base64Utils.decode(Base64Utils.pad(parts[4]));
        byte[] hash = Base64Utils.decode(Base64Utils.pad(parts[5]));
        return new Parsed(variant, Argon2Params.of(memoryKb, iterations, parallelism, hash.length), salt, hash);
    }

    private static byte[] toBytes(char[] password) {
        if (password == null) {
            throw new CryptoException("口令不能为空");
        }
        byte[] bytes = new String(password).getBytes(StandardCharsets.UTF_8);
        Arrays.fill(password, '\0');
        return bytes;
    }

    private record Parsed(Argon2Variant variant, Argon2Params params, byte[] salt, byte[] hash) {
    }
}
