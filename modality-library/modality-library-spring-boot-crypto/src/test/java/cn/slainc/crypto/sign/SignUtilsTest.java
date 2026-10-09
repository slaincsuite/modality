package cn.slainc.crypto.sign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.asymmetric.EcCurve;
import cn.slainc.crypto.asymmetric.EcdsaUtils;
import cn.slainc.crypto.asymmetric.EdDsaAlgorithm;
import cn.slainc.crypto.asymmetric.EdDsaUtils;
import cn.slainc.crypto.asymmetric.RsaKeySize;
import cn.slainc.crypto.asymmetric.RsaUtils;
import cn.slainc.crypto.sm.Sm2Utils;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;

class SignUtilsTest {

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
    void exposesAlgorithmMetadata() {
        assertTrue(SignatureAlgorithm.SM3_WITH_SM2.isSm2());
        assertEquals("SHA-256", SignatureAlgorithm.SHA256_WITH_RSA_PSS.pssDigest());
        assertEquals(32, SignatureAlgorithm.SHA256_WITH_RSA_PSS.pssSaltLength());
        assertEquals("Ed25519", SignatureAlgorithm.ED25519.jcaName());
    }
}
