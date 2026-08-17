package cn.slainc.crypto.key;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.codec.HexUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class MapCryptoKeyProviderTest {

    private static final String KEY_ONE = "0123456789abcdeffedcba9876543210";

    private static final String KEY_TWO = "fedcba98765432100123456789abcdef";

    @Test
    void readsKeysFromMap() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("2024", "hex:" + KEY_ONE);
        keys.put("2025", "hex:" + KEY_TWO);
        MapCryptoKeyProvider provider = new MapCryptoKeyProvider(keys, "2025", "SM4");
        assertEquals("2025", provider.currentKeyId());
        assertArrayEquals(HexUtils.decode(KEY_TWO), provider.currentKeyBytes());
        assertArrayEquals(HexUtils.decode(KEY_ONE), provider.key("2024").getEncoded());
        assertEquals(2, provider.keyIds().size());
        assertTrue(provider.contains("2024"));
        assertFalse(provider.contains("2026"));
        assertThrows(CryptoException.class, () -> provider.key("2026"));
    }

    @Test
    void defaultsToFirstKeyWhenCurrentIsMissing() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("only", "hex:" + KEY_ONE);
        MapCryptoKeyProvider provider = MapCryptoKeyProvider.sm4(keys);
        assertEquals("only", provider.currentKeyId());
        assertArrayEquals(HexUtils.decode(KEY_ONE), provider.currentKey().getEncoded());
    }

    @Test
    void readsKeysFromProperties() {
        Properties properties = new Properties();
        properties.setProperty("crypto.key.current", "b");
        properties.setProperty("crypto.key.a", "hex:" + KEY_ONE);
        properties.setProperty("crypto.key.b", "hex:" + KEY_TWO);
        properties.setProperty("other.setting", "ignored");
        MapCryptoKeyProvider provider = MapCryptoKeyProvider.fromProperties(properties, "crypto.key");
        assertEquals("b", provider.currentKeyId());
        assertArrayEquals(HexUtils.decode(KEY_TWO), provider.currentKey().getEncoded());
        assertEquals(2, provider.keyIds().size());
        MapCryptoKeyProvider defaultProvider = MapCryptoKeyProvider.fromProperties(properties);
        assertEquals("b", defaultProvider.currentKeyId());
    }

    @Test
    void rejectsIllegalConfiguration() {
        assertThrows(CryptoException.class, () -> new MapCryptoKeyProvider(Map.of()));
        assertThrows(CryptoException.class, () -> new MapCryptoKeyProvider(Map.of("a", "hex:" + KEY_ONE), "b", "SM4"));
        assertThrows(CryptoException.class, () -> MapCryptoKeyProvider.fromProperties(null, "crypto.key"));
        SecretKey key = SecretKeyUtils.sm4Key(HexUtils.decode(KEY_ONE));
        assertArrayEquals(HexUtils.decode(KEY_ONE), key.getEncoded());
    }
}
