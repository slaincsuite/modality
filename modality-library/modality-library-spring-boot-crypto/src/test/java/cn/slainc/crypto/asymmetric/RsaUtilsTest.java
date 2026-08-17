package cn.slainc.crypto.asymmetric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.random.SecureRandomUtils;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.junit.jupiter.api.Test;

class RsaUtilsTest {

    private static final byte[] DATA = "modality RSA 测试数据".getBytes(StandardCharsets.UTF_8);

    @Test
    void generatesRequestedKeySizes() {
        for (RsaKeySize keySize : RsaKeySize.values()) {
            KeyPair keyPair = RsaUtils.generateKeyPair(keySize);
            assertEquals(keySize.bits(), ((java.security.interfaces.RSAPublicKey) keyPair.getPublic()).getModulus().bitLength());
        }
    }

    @Test
    void roundTripsEveryTransformation() {
        KeyPair keyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_2048);
        for (RsaTransformation transformation : RsaTransformation.values()) {
            byte[] cipherText = RsaUtils.encrypt(transformation, keyPair.getPublic(), DATA);
            assertArrayEquals(DATA, RsaUtils.decrypt(transformation, keyPair.getPrivate(), cipherText));
        }
        assertTrue(RsaTransformation.OAEP_SHA256.isOaep());
        assertFalse(RsaTransformation.PKCS1.isOaep());
        assertEquals("SHA-384", RsaTransformation.OAEP_SHA384.digest());
        assertEquals("RSA/ECB/PKCS1Padding", RsaTransformation.PKCS1.transformation());
    }

    @Test
    void signsAndVerifiesEveryAlgorithm() {
        KeyPair keyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_2048);
        for (RsaSignatureAlgorithm algorithm : RsaSignatureAlgorithm.values()) {
            byte[] signature = RsaUtils.sign(algorithm, keyPair.getPrivate(), DATA);
            assertTrue(RsaUtils.verify(algorithm, keyPair.getPublic(), DATA, signature));
            assertFalse(RsaUtils.verify(algorithm, keyPair.getPublic(), "other".getBytes(StandardCharsets.UTF_8), signature));
            signature[signature.length - 1] ^= 0x01;
            assertFalse(RsaUtils.verify(algorithm, keyPair.getPublic(), DATA, signature));
        }
    }

    @Test
    void crossVerifiesWithSunRsaSign() throws Exception {
        KeyPair keyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_2048);
        byte[] pkcs1 = RsaUtils.sign(RsaSignatureAlgorithm.SHA256_WITH_RSA, keyPair.getPrivate(), DATA);
        Signature sun = Signature.getInstance("SHA256withRSA");
        sun.initVerify(keyPair.getPublic());
        sun.update(DATA);
        assertTrue(sun.verify(pkcs1));

        byte[] pss = RsaUtils.sign(RsaSignatureAlgorithm.SHA256_WITH_RSA_PSS, keyPair.getPrivate(), DATA);
        Signature sunPss = Signature.getInstance("RSASSA-PSS");
        sunPss.setParameter(new PSSParameterSpec("SHA-256", "MGF1", new MGF1ParameterSpec("SHA-256"), 32, 1));
        sunPss.initVerify(keyPair.getPublic());
        sunPss.update(DATA);
        assertTrue(sunPss.verify(pss));
    }

    @Test
    void crossDecryptsWithSunJceOaep() throws Exception {
        KeyPair keyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_2048);
        byte[] cipherText = RsaUtils.encrypt(RsaTransformation.OAEP_SHA256, keyPair.getPublic(), DATA);
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate(),
                new OAEPParameterSpec("SHA-256", "MGF1", new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT));
        assertArrayEquals(DATA, cipher.doFinal(cipherText));
    }

    @Test
    void rejectsIllegalUsage() {
        KeyPair keyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_1024);
        byte[] tooLong = SecureRandomUtils.nextBytes(200);
        assertThrows(CryptoException.class, () -> RsaUtils.encrypt(RsaTransformation.PKCS1, keyPair.getPublic(), tooLong));
        KeyPair other = RsaUtils.generateKeyPair(RsaKeySize.RSA_1024);
        byte[] cipherText = RsaUtils.encrypt(RsaTransformation.PKCS1, keyPair.getPublic(), DATA);
        assertThrows(CryptoException.class, () -> RsaUtils.decrypt(RsaTransformation.PKCS1, other.getPrivate(), cipherText));
        byte[] signature = RsaUtils.sign(RsaSignatureAlgorithm.SHA256_WITH_RSA, keyPair.getPrivate(), DATA);
        assertFalse(RsaUtils.verify(RsaSignatureAlgorithm.SHA256_WITH_RSA, other.getPublic(), DATA, signature));
    }
}
