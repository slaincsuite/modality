package cn.slainc.crypto.cert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.asymmetric.EcCurve;
import cn.slainc.crypto.asymmetric.EcdsaUtils;
import cn.slainc.crypto.asymmetric.EdDsaAlgorithm;
import cn.slainc.crypto.asymmetric.EdDsaUtils;
import cn.slainc.crypto.asymmetric.RsaKeySize;
import cn.slainc.crypto.asymmetric.RsaUtils;
import cn.slainc.crypto.sm.Sm2Utils;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CertificateGeneratorTest {

    @Test
    void issuesCertificatesFromRsaCa() throws Exception {
        KeyPair caKeyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_2048);
        X509Certificate ca = CertificateGenerator.selfSignedCa(caKeyPair, "CN=modality-ca,O=slainc", Duration.ofDays(3650));
        CertificateSpec spec = CertificateSpec.of("CN=leaf.example.com,O=slainc", Duration.ofDays(90))
                .withSerial(BigInteger.valueOf(42))
                .withSubjectAlternativeNames("leaf.example.com")
                .withKeyUsages("digitalSignature", "keyEncipherment")
                .withExtendedKeyUsages("serverAuth", "clientAuth");
        X509Certificate leaf = CertificateGenerator.issue(caKeyPair, ca, RsaUtils.generateKeyPair(RsaKeySize.RSA_1024).getPublic(), spec);
        assertEquals(BigInteger.valueOf(42), leaf.getSerialNumber());
        assertEquals(ca.getSubjectX500Principal(), leaf.getIssuerX500Principal());
        assertEquals("O=slainc,CN=leaf.example.com", leaf.getSubjectX500Principal().getName());
        assertTrue(X509Utils.verify(leaf, ca.getPublicKey()));
        assertEquals(-1, leaf.getBasicConstraints());
        assertTrue(leaf.getKeyUsage()[0]);
        assertTrue(leaf.getKeyUsage()[2]);
        assertFalse(leaf.getExtendedKeyUsage().isEmpty());
        assertEquals(List.of("dns:leaf.example.com"), X509Utils.info(leaf).subjectAlternativeNames());
        assertTrue(X509Utils.isChainValid(List.of(leaf, ca)));
    }

    @Test
    void issuesCertificatesWithEverySignerType() {
        assertIssuer(RsaUtils.generateKeyPair(RsaKeySize.RSA_1024));
        assertIssuer(EcdsaUtils.generateKeyPair(EcCurve.P_256));
        assertIssuer(Sm2Utils.generateKeyPair());
        assertIssuer(EdDsaUtils.generateKeyPair(EdDsaAlgorithm.ED25519));
    }

    @Test
    void createsCertificateAuthoritiesWithKeyCertSign() throws Exception {
        KeyPair keyPair = EcdsaUtils.generateKeyPair(EcCurve.P_256);
        X509Certificate ca = CertificateGenerator.selfSignedCa(keyPair, "CN=modality-ca", Duration.ofDays(100));
        assertTrue(ca.getBasicConstraints() >= 0);
        assertTrue(ca.getKeyUsage()[5]);
        assertTrue(X509Utils.isSelfSigned(ca));
        assertEquals("EC", ca.getPublicKey().getAlgorithm());
        assertTrue(CertificateGenerator.toPem(ca).contains("BEGIN CERTIFICATE"));
    }

    @Test
    void rejectsIllegalSpecifications() {
        assertThrows(CryptoException.class, () -> CertificateSpec.of("", Duration.ofDays(1)));
        assertThrows(CryptoException.class, () -> CertificateSpec.of(null, Duration.ofDays(1)));
        KeyPair keyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_1024);
        CertificateSpec unknownUsage = CertificateSpec.of("CN=modality", Duration.ofDays(1)).withKeyUsages("unknownUsage");
        assertThrows(CryptoException.class, () -> CertificateGenerator.selfSigned(keyPair, unknownUsage));
        CertificateSpec unknownPurpose = CertificateSpec.of("CN=modality", Duration.ofDays(1)).withExtendedKeyUsages("unknownPurpose");
        assertThrows(CryptoException.class, () -> CertificateGenerator.selfSigned(keyPair, unknownPurpose));
        CertificateSpec spec = CertificateSpec.of("CN=modality", Duration.ofDays(1));
        assertThrows(CryptoException.class, () -> CertificateGenerator.issue(keyPair, null, keyPair.getPublic(), spec));
        CertificateSpec defaulted = CertificateSpec.of("CN=modality", null);
        assertEquals(Duration.ofDays(365), defaulted.validity());
        assertEquals(-1, defaulted.pathLength());
        assertEquals(List.of(), defaulted.subjectAlternativeNames());
    }

    private static void assertIssuer(KeyPair caKeyPair) {
        X509Certificate ca = CertificateGenerator.selfSignedCa(caKeyPair, "CN=ca", Duration.ofDays(365));
        KeyPair leafKeyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_1024);
        X509Certificate leaf = CertificateGenerator.issue(caKeyPair, ca, leafKeyPair.getPublic(), CertificateSpec.of("CN=leaf", Duration.ofDays(10)));
        assertTrue(X509Utils.verify(leaf, ca.getPublicKey()));
        assertTrue(X509Utils.isChainValid(List.of(leaf, ca)));
        assertFalse(X509Utils.verify(leaf, leafKeyPair.getPublic()));
    }
}
