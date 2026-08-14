package cn.slainc.crypto.digest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import cn.slainc.crypto.CryptoProviders;
import cn.slainc.crypto.codec.HexUtils;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class Sm3UtilsTest {

    @Test
    void matchesGbT32905VectorAbc() {
        assertEquals("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0", Sm3Utils.digestHex("abc"));
    }

    @Test
    void matchesGbT32905VectorEmpty() {
        assertEquals("1ab21d8355cfa17f8e61194831e81a8f22bec8c728fefb747ed035eb5082aa2b", Sm3Utils.digestHex(""));
    }

    @Test
    void matchesGbT32905VectorRepeatedAbcd() {
        assertEquals("debe9ff92275b8a138604889c18e5a4d6fdb70e5387e5765293dcba39c0c5732", Sm3Utils.digestHex("abcd".repeat(16)));
    }

    @Test
    void producesThirtyTwoBytes() {
        assertEquals(32, Sm3Utils.DIGEST_LENGTH);
        assertEquals(32, Sm3Utils.digest("modality").length);
        assertEquals(64, Sm3Utils.digestHex("modality").length());
    }

    @Test
    void hashesLongMessagesConsistently() {
        byte[] data = new byte[1000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        assertEquals(Sm3Utils.digestHex(data), Sm3Utils.digestHex(data.clone()));
        assertNotEquals(Sm3Utils.digestHex(data), Sm3Utils.digestHex("modality"));
    }

    @Test
    void computesHmacSm3() throws Exception {
        byte[] key = "modality-secret".getBytes(StandardCharsets.UTF_8);
        byte[] data = "message".getBytes(StandardCharsets.UTF_8);
        byte[] hmac = Sm3Utils.hmac(key, data);
        assertEquals(32, hmac.length);
        assertFalse(HexUtils.encode(hmac).equals(Sm3Utils.digestHex(data)));
        Mac mac = Mac.getInstance("HmacSM3", CryptoProviders.bouncyCastle());
        mac.init(new SecretKeySpec(key, "HmacSM3"));
        assertEquals(HexUtils.encode(mac.doFinal(data)), HexUtils.encode(hmac));
        assertEquals(HexUtils.encode(hmac), Sm3Utils.hmacHex(key, "message"));
    }
}
