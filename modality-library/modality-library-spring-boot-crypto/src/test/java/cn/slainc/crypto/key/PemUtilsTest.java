package cn.slainc.crypto.key;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.asymmetric.EcCurve;
import cn.slainc.crypto.asymmetric.EcdsaUtils;
import cn.slainc.crypto.asymmetric.EdDsaAlgorithm;
import cn.slainc.crypto.asymmetric.EdDsaUtils;
import cn.slainc.crypto.asymmetric.RsaKeySize;
import cn.slainc.crypto.asymmetric.RsaUtils;
import cn.slainc.crypto.cert.CertificateGenerator;
import cn.slainc.crypto.cert.CertificateSpec;
import cn.slainc.crypto.cert.CsrUtils;
import cn.slainc.crypto.sm.Sm2Utils;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.jupiter.api.Test;

class PemUtilsTest {

    @Test
    void roundTripsRsaKeys() {
        KeyPair keyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_1024);
        assertKeyPair(keyPair);
    }

    @Test
    void roundTripsEcKeys() {
        assertKeyPair(EcdsaUtils.generateKeyPair(EcCurve.P_256));
        assertKeyPair(EcdsaUtils.generateKeyPair(EcCurve.P_384));
    }

    @Test
    void roundTripsSm2Keys() {
        assertKeyPair(Sm2Utils.generateKeyPair());
    }

    @Test
    void roundTripsEdDsaKeys() {
        assertKeyPair(EdDsaUtils.generateKeyPair(EdDsaAlgorithm.ED25519));
        assertKeyPair(EdDsaUtils.generateKeyPair(EdDsaAlgorithm.ED448));
    }

    @Test
    void roundTripsCertificatesAndRequests() {
        KeyPair keyPair = EcdsaUtils.generateKeyPair(EcCurve.P_256);
        X509Certificate certificate = CertificateGenerator.selfSigned(keyPair, CertificateSpec.of("CN=modality,O=slainc", Duration.ofDays(30)));
        String pem = PemUtils.encodeCertificate(certificate);
        assertTrue(pem.startsWith("-----BEGIN CERTIFICATE-----"));
        assertEquals(certificate.getSerialNumber(), PemUtils.readCertificate(pem).getSerialNumber());
        PKCS10CertificationRequest request = CsrUtils.generate(keyPair, "CN=modality", cn.slainc.crypto.sign.SignatureAlgorithm.SHA256_WITH_ECDSA);
        assertEquals(request.getSubject().toString(), PemUtils.readCertificationRequest(PemUtils.encodeCertificationRequest(request)).getSubject().toString());
    }

    @Test
    void decodesMultipleObjects() {
        KeyPair keyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_1024);
        X509Certificate certificate = CertificateGenerator.selfSigned(keyPair, CertificateSpec.of("CN=modality", Duration.ofDays(1)));
        String pem = PemUtils.encodePrivateKey(keyPair.getPrivate()) + PemUtils.encodeCertificate(certificate);
        List<Object> objects = PemUtils.decodeAll(pem);
        assertEquals(2, objects.size());
        assertInstanceOf(PrivateKey.class, objects.get(0));
        assertInstanceOf(X509Certificate.class, objects.get(1));
    }

    @Test
    void rejectsWrongTypes() {
        KeyPair keyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_1024);
        String publicPem = PemUtils.encodePublicKey(keyPair.getPublic());
        assertThrows(CryptoException.class, () -> PemUtils.readPrivateKey(publicPem));
        assertThrows(CryptoException.class, () -> PemUtils.readCertificate(publicPem));
        assertThrows(CryptoException.class, () -> PemUtils.readCertificationRequest(publicPem));
        assertThrows(CryptoException.class, () -> PemUtils.decode(""));
        assertThrows(CryptoException.class, () -> PemUtils.decode(null));
    }

    private static void assertKeyPair(KeyPair keyPair) {
        String privatePem = PemUtils.encodePrivateKey(keyPair.getPrivate());
        String publicPem = PemUtils.encodePublicKey(keyPair.getPublic());
        assertTrue(privatePem.startsWith("-----BEGIN PRIVATE KEY-----"));
        assertTrue(publicPem.startsWith("-----BEGIN PUBLIC KEY-----"));
        assertArrayEquals(keyPair.getPrivate().getEncoded(), PemUtils.readPrivateKey(privatePem).getEncoded());
        assertArrayEquals(keyPair.getPublic().getEncoded(), PemUtils.readPublicKey(publicPem).getEncoded());
    }
}
