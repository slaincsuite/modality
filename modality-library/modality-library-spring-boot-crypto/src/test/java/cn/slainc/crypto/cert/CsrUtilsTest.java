package cn.slainc.crypto.cert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.asymmetric.EcCurve;
import cn.slainc.crypto.asymmetric.EcdsaUtils;
import cn.slainc.crypto.asymmetric.RsaKeySize;
import cn.slainc.crypto.asymmetric.RsaUtils;
import cn.slainc.crypto.sm.Sm2Utils;
import cn.slainc.crypto.sign.SignatureAlgorithm;
import java.security.KeyPair;
import java.util.List;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.jupiter.api.Test;

class CsrUtilsTest {

    @Test
    void generatesAndVerifiesRsaRequest() {
        KeyPair keyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_2048);
        PKCS10CertificationRequest request = CsrUtils.generate(keyPair, "CN=modality.example.com,O=slainc");
        assertTrue(CsrUtils.verify(request));
        assertEquals("CN=modality.example.com,O=slainc", CsrUtils.subject(request));
        assertEquals(keyPair.getPublic(), CsrUtils.publicKey(request));
    }

    @Test
    void generatesAndVerifiesEcRequest() {
        KeyPair keyPair = EcdsaUtils.generateKeyPair(EcCurve.P_384);
        PKCS10CertificationRequest request = CsrUtils.generate(keyPair, "CN=ec.example.com", SignatureAlgorithm.SHA384_WITH_ECDSA, List.of("ec.example.com", "www.example.com"));
        assertTrue(CsrUtils.verify(request));
        assertEquals("CN=ec.example.com", CsrUtils.subject(request));
        assertEquals(1, request.getAttributes().length);
        assertEquals(keyPair.getPublic(), CsrUtils.publicKey(request));
    }

    @Test
    void generatesAndVerifiesSm2Request() {
        KeyPair keyPair = Sm2Utils.generateKeyPair();
        PKCS10CertificationRequest request = CsrUtils.generateSm2(keyPair, "CN=sm2.example.com");
        assertTrue(CsrUtils.verify(request));
        assertEquals("CN=sm2.example.com", CsrUtils.subject(request));
        assertEquals(keyPair.getPublic(), CsrUtils.publicKey(request));
        assertEquals(keyPair.getPublic(), CsrUtils.publicKey(CsrUtils.parse(CsrUtils.encode(request))));
    }

    @Test
    void roundTripsThroughPemAndDer() {
        KeyPair keyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_1024);
        PKCS10CertificationRequest request = CsrUtils.generate(keyPair, "CN=pem.example.com");
        String pem = CsrUtils.toPem(request);
        assertTrue(pem.startsWith("-----BEGIN CERTIFICATE REQUEST-----"));
        assertEquals(CsrUtils.subject(request), CsrUtils.subject(CsrUtils.parsePem(pem)));
        assertEquals(CsrUtils.subject(request), CsrUtils.subject(CsrUtils.parse(CsrUtils.encode(request))));
        assertTrue(CsrUtils.verify(CsrUtils.parse(CsrUtils.encode(request))));
    }

    @Test
    void detectsTamperedRequests() {
        KeyPair keyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_1024);
        byte[] encoded = CsrUtils.encode(CsrUtils.generate(keyPair, "CN=modality"));
        encoded[encoded.length - 5] ^= 0x01;
        assertFalse(CsrUtils.verifyQuietly(CsrUtils.parse(encoded)));
        assertThrows(CryptoException.class, () -> CsrUtils.parse(new byte[]{1, 2, 3}));
        assertThrows(CryptoException.class, () -> CsrUtils.generate(keyPair, " "));
        assertFalse(CsrUtils.verify(CsrUtils.parse(encoded)));
    }
}
