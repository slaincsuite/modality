package cn.slainc.crypto.digest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class DigestUtilsTest {

    @Test
    void matchesKnownVectorsForAbc() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", DigestUtils.md5Hex("abc"));
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", DigestUtils.sha1Hex("abc"));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", DigestUtils.sha256Hex("abc"));
        assertEquals("cb00753f45a35e8bb5a03d699ac65007272c32ab0eded1631a8b605a43ff5bed8086072ba1e7cc2358baeca134c825a7",
                DigestUtils.digestHex(DigestAlgorithm.SHA384, "abc"));
        assertEquals("ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
                DigestUtils.digestHex(DigestAlgorithm.SHA512, "abc"));
    }

    @Test
    void supportsBinaryInput() {
        byte[] data = {0x00, 0x01, 0x02, (byte) 0xFF};
        assertEquals(16, DigestUtils.md5(data).length);
        assertEquals(32, DigestUtils.sha256(data).length);
        assertNotEquals(DigestUtils.sha256Hex(data), DigestUtils.sha256Hex("modality"));
    }

    @Test
    void supportsSha3() {
        assertEquals("a7ffc6f8bf1ed76651c14756a061d662f580ff4de43b49fa82d80a4b80f8434a",
                DigestUtils.digestHex(DigestAlgorithm.SHA3_256, ""));
    }
}
