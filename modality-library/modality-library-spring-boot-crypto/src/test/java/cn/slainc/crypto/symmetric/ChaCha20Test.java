package cn.slainc.crypto.symmetric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.codec.HexUtils;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ChaCha20Test {

    private static final String SUNSCREEN = "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it.";

    private static final String RFC_KEY = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";

    private static final String RFC_NONCE = "000000090000004a00000000";

    @Test
    void matchesRfc8439BlockVector() {
        byte[] block = new byte[64];
        ChaCha20Core.block(HexUtils.decode(RFC_KEY), HexUtils.decode(RFC_NONCE), 1, block, 0);
        assertEquals("10f1e7e4d13b5915500fdd1fa32071c4c7d1f4c733c068030422aa9ac3d46c4e"
                + "d2826446079faa0914c2d705d98b02a2b5129cd1de164eb9cbd083e8a2503c4e", HexUtils.encode(block));
        assertArrayEquals(block, ChaCha20Core.keystream(HexUtils.decode(RFC_KEY), HexUtils.decode(RFC_NONCE), 1, 64));
    }

    @Test
    void matchesRfc8439EncryptionVector() {
        byte[] key = HexUtils.decode(RFC_KEY);
        byte[] nonce = HexUtils.decode("000000000000004a00000000");
        byte[] cipherText = ChaCha20Utils.process(key, nonce, 1, SUNSCREEN.getBytes(StandardCharsets.UTF_8));
        assertEquals("6e2e359a2568f98041ba0728dd0d6981e97e7aec1d4360c20a27afccfd9fae0b"
                + "f91b65c5524733ab8f593dabcd62b3571639d624e65152ab8f530c359f0861d8"
                + "07ca0dbf500d6a6156a38e088a22b65e52bc514d16ccf806818ce91ab7793736"
                + "5af90bbf74a35be6b40b8eedf2785e42874d", HexUtils.encode(cipherText));
        assertArrayEquals(SUNSCREEN.getBytes(StandardCharsets.UTF_8), ChaCha20Utils.decrypt(key, nonce, cipherText));
    }

    @Test
    void matchesRfc8439AeadVector() {
        byte[] key = HexUtils.decode("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f");
        byte[] nonce = HexUtils.decode("070000004041424344454647");
        byte[] aad = HexUtils.decode("50515253c0c1c2c3c4c5c6c7");
        byte[] sealed = ChaCha20Poly1305Utils.encrypt(key, nonce, aad, SUNSCREEN.getBytes(StandardCharsets.UTF_8));
        assertEquals("d31a8d34648e60db7b86afbc53ef7ec2a4aded51296e08fea9e2b5a736ee62d6"
                + "3dbea45e8ca9671282fafb69da92728b1a71de0a9e060b2905d6a5b67ecd3b36"
                + "92ddbd7f2d778b8c9803aee328091b58fab324e4fad675945585808b4831d7bc"
                + "3ff4def08e4b7a9de576d26586cec64b6116" + "1ae10b594f09e26a7e902ecbd0600691", HexUtils.encode(sealed));
        assertArrayEquals(SUNSCREEN.getBytes(StandardCharsets.UTF_8), ChaCha20Poly1305Utils.decrypt(key, nonce, aad, sealed));
    }

    @Test
    void detectsTamperedAeadData() {
        byte[] key = ChaCha20Poly1305Utils.generateKey();
        byte[] nonce = ChaCha20Poly1305Utils.generateNonce();
        byte[] sealed = ChaCha20Poly1305Utils.encrypt(key, nonce, "aad".getBytes(StandardCharsets.UTF_8), "secret".getBytes(StandardCharsets.UTF_8));
        sealed[0] ^= 0x01;
        assertThrows(CryptoException.class, () -> ChaCha20Poly1305Utils.decrypt(key, nonce, "aad".getBytes(StandardCharsets.UTF_8), sealed));
        byte[] clean = ChaCha20Poly1305Utils.encrypt(key, nonce, null, "secret".getBytes(StandardCharsets.UTF_8));
        assertThrows(CryptoException.class, () -> ChaCha20Poly1305Utils.decrypt(key, nonce, "other".getBytes(StandardCharsets.UTF_8), clean));
    }

    @Test
    void sealsWithRandomNonce() {
        byte[] key = ChaCha20Poly1305Utils.generateKey();
        byte[] data = "modality".getBytes(StandardCharsets.UTF_8);
        byte[] sealed = ChaCha20Poly1305Utils.seal(key, data);
        assertEquals(data.length + ChaCha20Poly1305Utils.NONCE_LENGTH + ChaCha20Poly1305Utils.TAG_LENGTH, sealed.length);
        assertArrayEquals(data, ChaCha20Poly1305Utils.open(key, sealed));
        assertFalse(java.util.Arrays.equals(sealed, ChaCha20Poly1305Utils.seal(key, data)));
    }

    @Test
    void rejectsIllegalParameters() {
        assertThrows(CryptoException.class, () -> ChaCha20Core.process(new byte[16], new byte[12], 0, new byte[8]));
        assertThrows(CryptoException.class, () -> ChaCha20Core.process(new byte[32], new byte[8], 0, new byte[8]));
        assertThrows(CryptoException.class, () -> ChaCha20Core.keystream(new byte[32], new byte[12], -1, 8));
        assertThrows(CryptoException.class, () -> ChaCha20Poly1305Utils.decrypt(new byte[32], new byte[12], null, new byte[8]));
        assertThrows(CryptoException.class, () -> ChaCha20Poly1305Utils.encrypt(new byte[32], new byte[12], null, null));
    }
}
