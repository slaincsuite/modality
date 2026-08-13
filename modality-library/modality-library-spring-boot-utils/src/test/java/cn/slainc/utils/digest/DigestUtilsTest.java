package cn.slainc.utils.digest;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DigestUtilsTest {

    @Test
    void md5KnownVectors() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", DigestUtils.md5("abc"));
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", DigestUtils.md5(""));
    }

    @Test
    void shaKnownVectors() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", DigestUtils.sha1("abc"));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", DigestUtils.sha256("abc"));
        assertEquals(
                "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a"
                        + "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
                DigestUtils.sha512("abc"));
    }

    @Test
    void streamDigestMatchesByteDigest() {
        byte[] bytes = "stream-content".getBytes(StandardCharsets.UTF_8);
        assertEquals(DigestUtils.sha256(bytes),
                DigestUtils.sha256Stream(new ByteArrayInputStream(bytes)));
        assertEquals(DigestUtils.md5(bytes), DigestUtils.md5Stream(new ByteArrayInputStream(bytes)));
    }

    @Test
    void constantTimeEquals() {
        byte[] left = "abc".getBytes(StandardCharsets.UTF_8);
        assertTrue(DigestUtils.constantTimeEquals(left, "abc".getBytes(StandardCharsets.UTF_8)));
        assertFalse(DigestUtils.constantTimeEquals(left, "abd".getBytes(StandardCharsets.UTF_8)));
    }
}
