package cn.slainc.crypto.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ConstantTimeUtilsTest {

    @Test
    void comparesByteArrays() {
        assertTrue(ConstantTimeUtils.equals("modality".getBytes(StandardCharsets.UTF_8), "modality".getBytes(StandardCharsets.UTF_8)));
        assertFalse(ConstantTimeUtils.equals("modality".getBytes(StandardCharsets.UTF_8), "modalityx".getBytes(StandardCharsets.UTF_8)));
        assertFalse(ConstantTimeUtils.equals(new byte[]{1, 2}, new byte[]{1, 3}));
        assertTrue(ConstantTimeUtils.equals((byte[]) null, (byte[]) null));
        assertFalse(ConstantTimeUtils.equals(new byte[0], null));
    }

    @Test
    void comparesCharArrays() {
        assertTrue(ConstantTimeUtils.equals("secret".toCharArray(), "secret".toCharArray()));
        assertFalse(ConstantTimeUtils.equals("secret".toCharArray(), "secrez".toCharArray()));
        assertTrue(ConstantTimeUtils.equals((char[]) null, (char[]) null));
    }

    @Test
    void comparesStrings() {
        assertTrue(ConstantTimeUtils.equals("国密算法", "国密算法"));
        assertFalse(ConstantTimeUtils.equals("国密算法", "国密"));
        assertFalse(ConstantTimeUtils.equals("secret", null));
        assertTrue(ConstantTimeUtils.equals((String) null, (String) null));
    }
}
