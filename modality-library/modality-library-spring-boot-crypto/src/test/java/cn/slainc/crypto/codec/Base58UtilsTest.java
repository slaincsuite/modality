package cn.slainc.crypto.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.slainc.crypto.CryptoException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Base58UtilsTest {

    @Test
    void encodesKnownVectors() {
        assertEquals("", Base58Utils.encode(new byte[0]));
        assertEquals("2g", Base58Utils.encode(HexUtils.decode("61")));
        assertEquals("a3gV", Base58Utils.encode(HexUtils.decode("626262")));
        assertEquals("aPEr", Base58Utils.encode(HexUtils.decode("636363")));
        assertEquals("2cFupjhnEsSn59qHXstmK2ffpLv2", Base58Utils.encode(HexUtils.decode("73696d706c792061206c6f6e6720737472696e67")));
        assertEquals("1NS17iag9jJgTHD1VXjvLCEnZuQ3rJDE9L", Base58Utils.encode(HexUtils.decode("00eb15231dfceb60925886b67d065299925915aeb172c06647")));
        assertEquals("1111111111", Base58Utils.encode(HexUtils.decode("00000000000000000000")));
        assertEquals("StV1DL6CwTryKyV", Base58Utils.encode("hello world".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void decodesKnownVectors() {
        assertArrayEquals(HexUtils.decode("61"), Base58Utils.decode("2g"));
        assertArrayEquals(HexUtils.decode("73696d706c792061206c6f6e6720737472696e67"), Base58Utils.decode("2cFupjhnEsSn59qHXstmK2ffpLv2"));
        assertArrayEquals(HexUtils.decode("00000000000000000000"), Base58Utils.decode("1111111111"));
        assertArrayEquals(new byte[0], Base58Utils.decode(""));
    }

    @Test
    void roundTripsRandomData() {
        for (int length = 1; length <= 64; length++) {
            byte[] bytes = new byte[length];
            for (int i = 0; i < length; i++) {
                bytes[i] = (byte) (i * 31 + length);
            }
            assertArrayEquals(bytes, Base58Utils.decode(Base58Utils.encode(bytes)));
        }
    }

    @Test
    void supportsCheckedPayload() {
        byte[] payload = HexUtils.decode("0062e907b15cbf27d5425399ebf6f0fb50ebb88f18");
        String encoded = Base58Utils.encodeChecked(payload);
        assertArrayEquals(payload, Base58Utils.decodeChecked(encoded));
        char last = encoded.charAt(encoded.length() - 1);
        String tampered = encoded.substring(0, encoded.length() - 1) + (last == '1' ? '2' : '1');
        assertThrows(CryptoException.class, () -> Base58Utils.decodeChecked(tampered));
    }

    @Test
    void rejectsIllegalCharacters() {
        assertThrows(CryptoException.class, () -> Base58Utils.decode("0OIl"));
        assertThrows(CryptoException.class, () -> Base58Utils.decodeChecked("1"));
    }
}
