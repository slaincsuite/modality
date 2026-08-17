package cn.slainc.crypto.asymmetric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.CryptoProviders;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import org.bouncycastle.crypto.signers.StandardDSAEncoding;
import org.junit.jupiter.api.Test;

class EcdsaUtilsTest {

    private static final byte[] DATA = "modality ECDSA 测试数据".getBytes(StandardCharsets.UTF_8);

    @Test
    void generatesEveryCurve() {
        for (EcCurve curve : EcCurve.values()) {
            KeyPair keyPair = EcdsaUtils.generateKeyPair(curve);
            ECKey ecKey = (ECKey) keyPair.getPublic();
            assertEquals(curve.fieldSize(), ecKey.getParams().getCurve().getField().getFieldSize());
            assertEquals(curve.fieldSize(), curve.fieldSize());
            assertEquals(EcdsaSignatureAlgorithm.forFieldSize(curve.fieldSize()), EcdsaUtils.defaultAlgorithm(keyPair.getPrivate()));
        }
    }

    @Test
    void roundTripsEveryCurve() {
        for (EcCurve curve : EcCurve.values()) {
            KeyPair keyPair = EcdsaUtils.generateKeyPair(curve);
            byte[] signature = EcdsaUtils.sign(keyPair.getPrivate(), DATA);
            assertTrue(EcdsaUtils.verify(keyPair.getPublic(), DATA, signature));
            assertFalse(EcdsaUtils.verify(keyPair.getPublic(), "other".getBytes(StandardCharsets.UTF_8), signature));
            signature[signature.length - 2] ^= 0x01;
            assertFalse(EcdsaUtils.verify(keyPair.getPublic(), DATA, signature));
        }
    }

    @Test
    void crossVerifiesWithSunEc() throws Exception {
        KeyPair keyPair = EcdsaUtils.generateKeyPair(EcCurve.P_256);
        byte[] signature = EcdsaUtils.sign(EcdsaSignatureAlgorithm.SHA256_WITH_ECDSA, keyPair.getPrivate(), DATA);
        Signature sun = Signature.getInstance("SHA256withECDSA");
        sun.initVerify(keyPair.getPublic());
        sun.update(DATA);
        assertTrue(sun.verify(signature));
    }

    @Test
    void verifiesRfc6979Vector() throws Exception {
        BigInteger order = new BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16);
        BigInteger r = new BigInteger("EFD48B2AACB6A8FD1140DD9CD45E81D69D2C877B56AAF991C34D0EA84EAF3716", 16);
        BigInteger s = new BigInteger("F7CB1C942D657C41D436C7A1B6E29F65F3E900DBB9AFF4064DC4AB2F843ACDA8", 16);
        byte[] signature = StandardDSAEncoding.INSTANCE.encode(order, r, s);
        PublicKey publicKey = publicKey();
        assertTrue(EcdsaUtils.verify(EcdsaSignatureAlgorithm.SHA256_WITH_ECDSA, publicKey, "sample".getBytes(StandardCharsets.UTF_8), signature));
        assertFalse(EcdsaUtils.verify(EcdsaSignatureAlgorithm.SHA256_WITH_ECDSA, publicKey, "samplex".getBytes(StandardCharsets.UTF_8), signature));
    }

    private static PublicKey publicKey() throws Exception {
        BigInteger x = new BigInteger("60FED4BA255A9D31C961EB74C6356D68C049B8923B61FA6CE669622E60F29FB6", 16);
        BigInteger y = new BigInteger("7903FE1008B8BC99A41AE9E95628BC64F2F1B20C2D7E9F5177A3C294D4462299", 16);
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC", CryptoProviders.bouncyCastle());
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
        return KeyFactory.getInstance("EC", CryptoProviders.bouncyCastle())
                .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), spec));
    }

    @Test
    void rejectsNonEcKeys() {
        assertThrows(CryptoException.class, () -> EcdsaUtils.defaultAlgorithm(RsaUtils.generateKeyPair(RsaKeySize.RSA_1024).getPrivate()));
    }
}
