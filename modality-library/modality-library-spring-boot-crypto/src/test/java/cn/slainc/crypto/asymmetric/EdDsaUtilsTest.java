package cn.slainc.crypto.asymmetric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.codec.HexUtils;
import cn.slainc.crypto.jwt.Jwk;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EdDsaUtilsTest {

    @Test
    void generatesBothAlgorithms() {
        for (EdDsaAlgorithm algorithm : EdDsaAlgorithm.values()) {
            KeyPair keyPair = EdDsaUtils.generateKeyPair(algorithm);
            assertEquals(algorithm.keyLength(), Base64Utils.decodeUrl(String.valueOf(Jwk.fromPublicKey(keyPair.getPublic()).toMap().get("x"))).length);
            byte[] data = "modality".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] signature = EdDsaUtils.sign(algorithm, keyPair.getPrivate(), data);
            assertTrue(EdDsaUtils.verify(algorithm, keyPair.getPublic(), data, signature));
            assertFalse(EdDsaUtils.verify(algorithm, keyPair.getPublic(), "other".getBytes(java.nio.charset.StandardCharsets.UTF_8), signature));
            signature[0] ^= 0x01;
            assertFalse(EdDsaUtils.verify(algorithm, keyPair.getPublic(), data, signature));
        }
    }

    @Test
    void matchesRfc8032Ed25519TestOne() {
        assertSignature("Ed25519",
                "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60",
                "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a",
                "",
                "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b");
    }

    @Test
    void matchesRfc8032Ed25519TestTwo() {
        assertSignature("Ed25519",
                "4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb",
                "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c",
                "72",
                "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00");
    }

    @Test
    void matchesRfc8032Ed448BlankTest() {
        assertSignature("Ed448",
                "6c82a562cb808d10d632be89c8513ebf6c929f34ddfa8c9f63c9960ef6e348a3528c8a3fcc2f044e39a3fc5b94492f8f032e7549a20098f95b",
                "5fd7449b59b461fd2ce787ec616ad46a1da1342485a70e1f8a0ea75d80e96778edf124769b46c7061bd6783df1e50f6cd1fa1abeafe8256180",
                "",
                "533a37f6bbe457251f023c0d88f976ae2dfb504a843e34d2074fd823d41a591f2b233f034f628281f2fd7a22ddd47d7828c59bd0a21bfd3980ff0d2028d4b18a9df63e006c5d1c2d345b925d8dc00b4104852db99ac5c7cdda8530a113a0f4dbb61149f05a7363268c71d95808ff2e652600");
    }

    @Test
    void matchesRfc8032Ed25519TestThree() {
        assertSignature("Ed25519",
                "c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7",
                "fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025",
                "af82",
                "6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3ac18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a");
    }

    private static void assertSignature(String curve, String secretKey, String publicKey, String message, String signature) {
        PrivateKey privateKey = privateKey(curve, secretKey);
        PublicKey expected = publicKey(curve, publicKey);
        byte[] messageBytes = HexUtils.decode(message);
        byte[] actual = EdDsaUtils.sign(algorithm(curve), privateKey, messageBytes);
        assertArrayEquals(HexUtils.decode(signature), actual);
        assertTrue(EdDsaUtils.verify(algorithm(curve), expected, messageBytes, HexUtils.decode(signature)));
        assertFalse(EdDsaUtils.verify(algorithm(curve), expected, new byte[]{(byte) 0xFF}, HexUtils.decode(signature)));
    }

    private static PrivateKey privateKey(String curve, String secretKey) {
        return Jwk.fromMap(keyMap(curve, "d", secretKey)).toPrivateKey();
    }

    private static PublicKey publicKey(String curve, String publicKey) {
        return Jwk.fromMap(keyMap(curve, "x", publicKey)).toPublicKey();
    }

    private static Map<String, Object> keyMap(String curve, String name, String value) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("kty", "OKP");
        values.put("crv", curve);
        values.put(name, Base64Utils.encodeUrlNoPadding(HexUtils.decode(value)));
        return values;
    }

    private static EdDsaAlgorithm algorithm(String curve) {
        return "Ed448".equals(curve) ? EdDsaAlgorithm.ED448 : EdDsaAlgorithm.ED25519;
    }
}
