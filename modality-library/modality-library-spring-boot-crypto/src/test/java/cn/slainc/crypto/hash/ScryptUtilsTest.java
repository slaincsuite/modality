package cn.slainc.crypto.hash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.codec.HexUtils;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ScryptUtilsTest {

    @Test
    void matchesRfc7914Vectors() {
        assertEquals("77d6576238657b203b19ca42c18a0497f16b4844e3074ae8dfdffa3fede21442"
                        + "fcd0069ded0948f8326a753a0fc81f17e8d3e0fb2e0d3628cf35e20c38d18906",
                HexUtils.encode(ScryptUtils.derive(new byte[0], new byte[0], ScryptParams.of(16, 1, 1, 64))));
        assertEquals("fdbabe1c9d3472007856e7190d01e9fe7c6ad7cbc8237830e77376634b373162"
                        + "2eaf30d92e22a3886ff109279d9830dac727afb94a83ee6d8360cbdfa2cc0640",
                HexUtils.encode(ScryptUtils.derive("password", "NaCl", ScryptParams.of(1024, 8, 16, 64))));
        assertEquals("7023bdcb3afd7348461c06cd81fd38ebfda8fbba904f8e3ea9b543f6545da1f2"
                        + "d5432955613f0fcf62d49705242a9af9e61e85dc0d651e40dfcf017b45575887",
                HexUtils.encode(ScryptUtils.derive("pleaseletmein", "SodiumChloride", ScryptParams.of(16384, 8, 1, 64))));
    }

    @Test
    void hashesAndVerifiesEncodedValues() {
        String encoded = ScryptUtils.hashEncoded("modality".getBytes(StandardCharsets.UTF_8), ScryptParams.of(4096, 8, 1, 32));
        assertTrue(encoded.startsWith("scrypt$4096$8$1$"));
        assertTrue(ScryptUtils.verify("modality", encoded));
        assertFalse(ScryptUtils.verify("other", encoded));
        assertFalse(ScryptUtils.verify("modality", null));
        assertTrue(ScryptUtils.verify("modality".getBytes(StandardCharsets.UTF_8), encoded));
        assertEquals(32, ScryptUtils.derive(new byte[1], new byte[1], ScryptParams.defaults().withKeyLength(32)).length);
    }

    @Test
    void rejectsIllegalArguments() {
        assertThrows(CryptoException.class, () -> ScryptUtils.derive(new byte[0], new byte[0], ScryptParams.of(15, 1, 1, 32)));
        assertThrows(CryptoException.class, () -> ScryptUtils.derive(new byte[0], new byte[0], ScryptParams.of(1, 1, 1, 32)));
        assertThrows(CryptoException.class, () -> ScryptUtils.derive((byte[]) null, new byte[1], ScryptParams.defaults()));
        assertThrows(CryptoException.class, () -> ScryptUtils.derive((String) null, "salt", ScryptParams.defaults()));
        assertThrows(CryptoException.class, () -> ScryptUtils.verify("password", "scrypt$1$2"));
        assertEquals(16384, ScryptParams.defaults().cost());
        assertEquals(8, ScryptParams.defaults().blockSize());
        assertEquals(1, ScryptParams.defaults().parallelization());
        assertEquals(32, ScryptParams.defaults().keyLength());
    }
}
