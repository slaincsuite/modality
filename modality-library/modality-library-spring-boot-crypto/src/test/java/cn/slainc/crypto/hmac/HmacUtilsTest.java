package cn.slainc.crypto.hmac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.codec.HexUtils;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HmacUtilsTest {

    private static final byte[] RFC_KEY = new byte[20];

    static {
        for (int i = 0; i < RFC_KEY.length; i++) {
            RFC_KEY[i] = 0x0b;
        }
    }

    private static final byte[] RFC_DATA = "Hi There".getBytes(StandardCharsets.UTF_8);

    @Test
    void matchesRfc4231TestCaseOne() {
        assertEquals("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
                HmacUtils.hmacHex(HmacAlgorithm.HMAC_SHA256, RFC_KEY, RFC_DATA));
        assertEquals("87aa7cdea5ef619d4ff0b4241a1d6cb02379f4e2ce4ec2787ad0b30545e17cdedaa833b7d6b8a702038b274eaea3f4e4be9d914eeb61f1702e696c203a126854",
                HmacUtils.hmacHex(HmacAlgorithm.HMAC_SHA512, RFC_KEY, RFC_DATA));
    }

    @Test
    void matchesRfc2202TestCaseOne() {
        assertEquals("b617318655057264e28bc0b6fb378c8ef146be00", HmacUtils.hmacHex(HmacAlgorithm.HMAC_SHA1, RFC_KEY, RFC_DATA));
    }

    @Test
    void computesHmacSm3() {
        byte[] hmac = HmacUtils.hmac(HmacAlgorithm.HMAC_SM3, "key".getBytes(StandardCharsets.UTF_8), RFC_DATA);
        assertEquals(32, hmac.length);
        assertEquals(HexUtils.encode(hmac), HmacUtils.hmacHex(HmacAlgorithm.HMAC_SM3, "key".getBytes(StandardCharsets.UTF_8), "Hi There"));
        assertNotEquals(cn.slainc.crypto.digest.Sm3Utils.digestHex(RFC_DATA), HexUtils.encode(hmac));
    }

    @Test
    void verifiesHmacInConstantTime() {
        byte[] key = "modality".getBytes(StandardCharsets.UTF_8);
        byte[] hmac = HmacUtils.hmac(HmacAlgorithm.HMAC_SHA256, key, RFC_DATA);
        assertTrue(HmacUtils.verify(HmacAlgorithm.HMAC_SHA256, key, RFC_DATA, hmac));
        hmac[0] ^= 0x01;
        assertFalse(HmacUtils.verify(HmacAlgorithm.HMAC_SHA256, key, RFC_DATA, hmac));
    }

    @Test
    void producesBase64AndDifferentKeys() {
        byte[] key = "modality".getBytes(StandardCharsets.UTF_8);
        assertFalse(HmacUtils.hmacBase64(HmacAlgorithm.HMAC_SHA256, key, RFC_DATA).isEmpty());
        assertNotEquals(HmacUtils.hmacHex(HmacAlgorithm.HMAC_SHA384, key, RFC_DATA),
                HmacUtils.hmacHex(HmacAlgorithm.HMAC_SHA256, key, RFC_DATA));
    }
}
