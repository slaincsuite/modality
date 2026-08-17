package cn.slainc.crypto.sign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.asymmetric.EcCurve;
import cn.slainc.crypto.asymmetric.EcdsaUtils;
import cn.slainc.crypto.asymmetric.EdDsaAlgorithm;
import cn.slainc.crypto.asymmetric.EdDsaUtils;
import cn.slainc.crypto.asymmetric.RsaKeySize;
import cn.slainc.crypto.asymmetric.RsaUtils;
import cn.slainc.crypto.sm.Sm2Utils;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;

class SignUtilsTest {

    private static final byte[] DATA = "modality 统一签名门面".getBytes(StandardCharsets.UTF_8);

    @Test
    void detectsAlgorithmsForEveryKeyType() {
        KeyPair rsa = RsaUtils.generateKeyPair(RsaKeySize.RSA_1024);
        assertEquals(SignatureAlgorithm.SHA256_WITH_RSA, SignUtils.detect(rsa.getPrivate()));
        assertEquals(SignatureAlgorithm.SHA256_WITH_ECDSA, SignUtils.detect(EcdsaUtils.generateKeyPair(EcCurve.P_256).getPublic()));
        assertEquals(SignatureAlgorithm.SHA384_WITH_ECDSA, SignUtils.detect(EcdsaUtils.generateKeyPair(EcCurve.P_384).getPublic()));
        assertEquals(SignatureAlgorithm.SHA512_WITH_ECDSA, SignUtils.detect(EcdsaUtils.generateKeyPair(EcCurve.P_521).getPublic()));
        assertEquals(SignatureAlgorithm.SM3_WITH_SM2, SignUtils.detect(Sm2Utils.generateKeyPair().getPrivate()));
        assertEquals(SignatureAlgorithm.ED25519, SignUtils.detect(EdDsaUtils.generateKeyPair(EdDsaAlgorithm.ED25519).getPublic()));
        assertEquals(SignatureAlgorithm.ED448, SignUtils.detect(EdDsaUtils.generateKeyPair(EdDsaAlgorithm.ED448).getPublic()));
    }

    @Test
    void signsAndVerifiesWithEveryKeyType() {
        KeyPair rsa = RsaUtils.generateKeyPair(RsaKeySize.RSA_2048);
        assertRoundTrip(SignatureAlgorithm.SHA256_WITH_RSA, rsa);
        assertRoundTrip(SignatureAlgorithm.SHA384_WITH_RSA, rsa);
        assertRoundTrip(SignatureAlgorithm.SHA512_WITH_RSA, rsa);
        assertRoundTrip(SignatureAlgorithm.SHA256_WITH_RSA_PSS, rsa);
        assertRoundTrip(SignatureAlgorithm.SHA384_WITH_RSA_PSS, rsa);
        assertRoundTrip(SignatureAlgorithm.SHA512_WITH_RSA_PSS, rsa);
        assertRoundTrip(SignatureAlgorithm.SHA256_WITH_ECDSA, EcdsaUtils.generateKeyPair(EcCurve.P_256));
        assertRoundTrip(SignatureAlgorithm.SHA384_WITH_ECDSA, EcdsaUtils.generateKeyPair(EcCurve.P_384));
        assertRoundTrip(SignatureAlgorithm.SHA512_WITH_ECDSA, EcdsaUtils.generateKeyPair(EcCurve.P_521));
        assertRoundTrip(SignatureAlgorithm.SM3_WITH_SM2, Sm2Utils.generateKeyPair());
        assertRoundTrip(SignatureAlgorithm.ED25519, EdDsaUtils.generateKeyPair(EdDsaAlgorithm.ED25519));
        assertRoundTrip(SignatureAlgorithm.ED448, EdDsaUtils.generateKeyPair(EdDsaAlgorithm.ED448));
    }

    @Test
    void usesDetectedAlgorithmByDefault() {
        KeyPair keyPair = EcdsaUtils.generateKeyPair(EcCurve.P_256);
        byte[] signature = SignUtils.sign(keyPair.getPrivate(), DATA);
        assertTrue(SignUtils.verify(keyPair.getPublic(), DATA, signature));
    }

    @Test
    void verifiesQuietlyOnTamperedData() {
        KeyPair keyPair = EcdsaUtils.generateKeyPair(EcCurve.P_256);
        byte[] signature = SignUtils.sign(SignatureAlgorithm.SHA256_WITH_ECDSA, keyPair.getPrivate(), DATA);
        signature[3] ^= 0x01;
        assertFalse(SignUtils.verifyQuietly(SignatureAlgorithm.SHA256_WITH_ECDSA, keyPair.getPublic(), DATA, signature));
        assertTrue(SignUtils.verifyBase64(SignatureAlgorithm.SHA256_WITH_ECDSA, keyPair.getPublic(), DATA,
                SignUtils.signBase64(SignatureAlgorithm.SHA256_WITH_ECDSA, keyPair.getPrivate(), DATA)));
    }

    @Test
    void exposesAlgorithmMetadata() {
        assertTrue(SignatureAlgorithm.SHA256_WITH_RSA_PSS.isPss());
        assertFalse(SignatureAlgorithm.SHA256_WITH_RSA.isPss());
        assertTrue(SignatureAlgorithm.SM3_WITH_SM2.isSm2());
        assertEquals("SHA-256", SignatureAlgorithm.SHA256_WITH_RSA_PSS.pssDigest());
        assertEquals(32, SignatureAlgorithm.SHA256_WITH_RSA_PSS.pssSaltLength());
        assertEquals("Ed25519", SignatureAlgorithm.ED25519.jcaName());
    }

    private static void assertRoundTrip(SignatureAlgorithm algorithm, KeyPair keyPair) {
        byte[] signature = SignUtils.sign(algorithm, keyPair.getPrivate(), DATA);
        assertTrue(SignUtils.verify(algorithm, keyPair.getPublic(), DATA, signature), algorithm.name());
        assertFalse(SignUtils.verify(algorithm, keyPair.getPublic(), "other".getBytes(StandardCharsets.UTF_8), signature), algorithm.name());
    }
}
