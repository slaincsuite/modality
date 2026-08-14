package cn.slainc.crypto.digest;

import cn.slainc.crypto.codec.HexUtils;
import java.nio.charset.StandardCharsets;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;

public final class Sm3Utils {

    public static final int DIGEST_LENGTH = 32;

    public static final int BLOCK_LENGTH = 64;

    private Sm3Utils() {
    }

    public static byte[] digest(byte[] data) {
        SM3Digest digest = new SM3Digest();
        digest.update(data, 0, data.length);
        byte[] output = new byte[digest.getDigestSize()];
        digest.doFinal(output, 0);
        return output;
    }

    public static byte[] digest(String text) {
        return digest(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String digestHex(byte[] data) {
        return HexUtils.encode(digest(data));
    }

    public static String digestHex(String text) {
        return HexUtils.encode(digest(text));
    }

    public static byte[] hmac(byte[] key, byte[] data) {
        HMac mac = new HMac(new SM3Digest());
        mac.init(new KeyParameter(key));
        mac.update(data, 0, data.length);
        byte[] output = new byte[mac.getMacSize()];
        mac.doFinal(output, 0);
        return output;
    }

    public static byte[] hmac(byte[] key, String text) {
        return hmac(key, text.getBytes(StandardCharsets.UTF_8));
    }

    public static String hmacHex(byte[] key, byte[] data) {
        return HexUtils.encode(hmac(key, data));
    }

    public static String hmacHex(byte[] key, String text) {
        return HexUtils.encode(hmac(key, text));
    }
}
