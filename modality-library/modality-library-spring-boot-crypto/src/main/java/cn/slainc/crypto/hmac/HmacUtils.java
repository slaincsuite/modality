package cn.slainc.crypto.hmac;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.CryptoProviders;
import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.codec.HexUtils;
import cn.slainc.crypto.digest.Sm3Utils;
import cn.slainc.crypto.util.ConstantTimeUtils;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacUtils {

    private HmacUtils() {
    }

    public static byte[] hmac(HmacAlgorithm algorithm, byte[] key, byte[] data) {
        if (algorithm == HmacAlgorithm.HMAC_SM3) {
            return Sm3Utils.hmac(key, data);
        }
        try {
            Mac mac = Mac.getInstance(algorithm.jcaName(), CryptoProviders.bouncyCastle());
            mac.init(new SecretKeySpec(key, algorithm.jcaName()));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new CryptoException("无法计算 " + algorithm, exception);
        }
    }

    public static byte[] hmac(HmacAlgorithm algorithm, byte[] key, String text) {
        return hmac(algorithm, key, text.getBytes(StandardCharsets.UTF_8));
    }

    public static String hmacHex(HmacAlgorithm algorithm, byte[] key, byte[] data) {
        return HexUtils.encode(hmac(algorithm, key, data));
    }

    public static String hmacHex(HmacAlgorithm algorithm, byte[] key, String text) {
        return HexUtils.encode(hmac(algorithm, key, text));
    }

    public static String hmacBase64(HmacAlgorithm algorithm, byte[] key, byte[] data) {
        return Base64Utils.encode(hmac(algorithm, key, data));
    }

    public static boolean verify(HmacAlgorithm algorithm, byte[] key, byte[] data, byte[] expected) {
        return ConstantTimeUtils.equals(hmac(algorithm, key, data), expected);
    }
}
