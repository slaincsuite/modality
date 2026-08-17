package cn.slainc.crypto.sm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.CryptoProviders;
import cn.slainc.crypto.codec.HexUtils;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.junit.jupiter.api.Test;

class Sm2UtilsTest {

    private static final String VECTOR_PRIVATE_KEY = "110E7973206F68C19EE5F7328C036F26911C8C73B4E4F36AE3291097F8984FFC";

    private static final byte[] VECTOR_ID = "sm2test@example.com".getBytes(StandardCharsets.UTF_8);

    private static final byte[] VECTOR_MESSAGE = HexUtils.decode("686920636861707079");

    private static final String VECTOR_SIGNATURE = "05890B9077B92E47B17A1FF42A814280E556AFD92B4A98B9670BF8B1A274C2FA"
            + "E3ABBB8DB2B6ECD9B24ECCEA7F679FB9A4B1DB52F4AA985E443AD73237FA1993";

    @Test
    void generatesKeysOnSm2Curve() {
        KeyPair keyPair = Sm2Utils.generateKeyPair();
        ECKey ecKey = (ECKey) keyPair.getPublic();
        assertEquals(new BigInteger("FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFF7203DF6B21C6052B53BBF40939D54123", 16), ecKey.getParams().getOrder());
        assertEquals("EC", keyPair.getPrivate().getAlgorithm());
        assertNotEquals(HexUtils.encode(Sm2Utils.generateKeyPair().getPublic().getEncoded()),
                HexUtils.encode(keyPair.getPublic().getEncoded()));
    }

    @Test
    void encryptsAndDecryptsInBothModes() {
        KeyPair keyPair = Sm2Utils.generateKeyPair();
        byte[] data = "modality 国密 SM2 加解密".getBytes(StandardCharsets.UTF_8);
        for (Sm2Mode mode : Sm2Mode.values()) {
            byte[] cipherText = Sm2Utils.encrypt(keyPair.getPublic(), data, mode);
            assertArrayEquals(data, Sm2Utils.decrypt(keyPair.getPrivate(), cipherText, mode));
        }
        byte[] defaultCipher = Sm2Utils.encrypt(keyPair.getPublic(), data);
        assertArrayEquals(data, Sm2Utils.decrypt(keyPair.getPrivate(), defaultCipher));
        byte[] c1c2c3 = Sm2Utils.encrypt(keyPair.getPublic(), data, Sm2Mode.C1C2C3);
        assertThrows(CryptoException.class, () -> Sm2Utils.decrypt(keyPair.getPrivate(), c1c2c3));
    }

    @Test
    void encryptsBinaryData() {
        KeyPair keyPair = Sm2Utils.generateKeyPair();
        assertThrows(CryptoException.class, () -> Sm2Utils.encrypt(keyPair.getPublic(), new byte[0]));
        byte[] data = new byte[64];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 7);
        }
        assertArrayEquals(data, Sm2Utils.decrypt(keyPair.getPrivate(), Sm2Utils.encrypt(keyPair.getPublic(), data)));
    }

    @Test
    void signsAndVerifies() {
        KeyPair keyPair = Sm2Utils.generateKeyPair();
        byte[] data = "modality sign".getBytes(StandardCharsets.UTF_8);
        byte[] signature = Sm2Utils.sign(keyPair.getPrivate(), data);
        assertEquals(Sm2Utils.SIGNATURE_LENGTH, signature.length);
        assertTrue(Sm2Utils.verify(keyPair.getPublic(), data, signature));
        assertFalse(Sm2Utils.verify(keyPair.getPublic(), "other".getBytes(StandardCharsets.UTF_8), signature));
        signature[0] ^= 0x01;
        assertFalse(Sm2Utils.verify(keyPair.getPublic(), data, signature));
        byte[] derSignature = Sm2Utils.signDer(keyPair.getPrivate(), data);
        assertTrue(Sm2Utils.verifyDer(keyPair.getPublic(), data, derSignature));
        assertFalse(Sm2Utils.verifyDer(keyPair.getPublic(), data, Sm2Utils.sign(keyPair.getPrivate(), data)));
    }

    @Test
    void matchesGmSignatureVectorWithFixedNonce() throws Exception {
        PrivateKey privateKey = privateKey(VECTOR_PRIVATE_KEY);
        byte[] signature = Sm2Utils.sign(privateKey, VECTOR_MESSAGE, VECTOR_ID, new FixedSecureRandom("3174C6FFC3C279D2422F3FC0A9F3E574674A4490FE45A5325CAF7D3EC4C8F96C"));
        assertEquals(VECTOR_SIGNATURE, HexUtils.encodeUpper(signature));
        byte[] derSignature = Sm2Utils.signDer(privateKey, VECTOR_MESSAGE, VECTOR_ID, new FixedSecureRandom("3174C6FFC3C279D2422F3FC0A9F3E574674A4490FE45A5325CAF7D3EC4C8F96C"));
        assertTrue(Sm2Utils.verifyDer(publicKey(VECTOR_PRIVATE_KEY), VECTOR_MESSAGE, derSignature, VECTOR_ID));
    }

    @Test
    void verifiesGmSignatureVector() throws Exception {
        PublicKey publicKey = publicKey(VECTOR_PRIVATE_KEY);
        assertTrue(Sm2Utils.verify(publicKey, VECTOR_MESSAGE, HexUtils.decode(VECTOR_SIGNATURE), VECTOR_ID));
        assertFalse(Sm2Utils.verify(publicKey, VECTOR_MESSAGE, HexUtils.decode(VECTOR_SIGNATURE)));
        byte[] tampered = HexUtils.decode(VECTOR_SIGNATURE);
        tampered[10] ^= 0x01;
        assertFalse(Sm2Utils.verify(publicKey, VECTOR_MESSAGE, tampered, VECTOR_ID));
    }

    @Test
    void supportsBase64AndHexHelpers() {
        KeyPair keyPair = Sm2Utils.generateKeyPair();
        byte[] data = "helpers".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(data, Sm2Utils.decryptBase64(keyPair.getPrivate(), Sm2Utils.encryptBase64(keyPair.getPublic(), data)));
        String signature = Sm2Utils.signHex(keyPair.getPrivate(), data);
        assertTrue(Sm2Utils.verifyHex(keyPair.getPublic(), data, signature));
        assertTrue(Sm2Utils.verifyBase64(keyPair.getPublic(), data, Sm2Utils.signBase64(keyPair.getPrivate(), data)));
    }

    private static PrivateKey privateKey(String hex) throws Exception {
        return KeyFactory.getInstance("EC", CryptoProviders.bouncyCastle())
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(hex, 16), parameters()));
    }

    private static PublicKey publicKey(String hex) throws Exception {
        BigInteger d = new BigInteger(hex, 16);
        org.bouncycastle.math.ec.ECPoint point = GMNamedCurves.getByName("sm2p256v1").getG().multiply(d).normalize();
        ECPoint publicPoint = new ECPoint(point.getAffineXCoord().toBigInteger(), point.getAffineYCoord().toBigInteger());
        return KeyFactory.getInstance("EC", CryptoProviders.bouncyCastle()).generatePublic(new ECPublicKeySpec(publicPoint, parameters()));
    }

    private static ECParameterSpec parameters() throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC", CryptoProviders.bouncyCastle());
        parameters.init(new ECGenParameterSpec("sm2p256v1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    private static final class FixedSecureRandom extends SecureRandom {

        private final byte[] value;

        private FixedSecureRandom(String hex) {
            this.value = HexUtils.decode(hex);
        }

        @Override
        public void nextBytes(byte[] bytes) {
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = value[i % value.length];
            }
        }
    }
}
