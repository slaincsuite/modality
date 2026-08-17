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
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class X509UtilsTest {

    private static final CertificateSpec LEAF_SPEC = CertificateSpec.of("CN=modality.example.com,O=slainc", Duration.ofDays(365))
            .withSubjectAlternativeNames("modality.example.com", "127.0.0.1")
            .withServerAuth();

    @Test
    void parsesAndDescribesCertificates() throws Exception {
        KeyPair keyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_1024);
        X509Certificate certificate = CertificateGenerator.selfSigned(keyPair, LEAF_SPEC);
        X509Certificate parsed = X509Utils.parsePem(X509Utils.toPem(certificate));
        assertEquals(certificate.getSerialNumber(), parsed.getSerialNumber());
        assertEquals(certificate.getSerialNumber(), X509Utils.parse(certificate.getEncoded()).getSerialNumber());
        CertificateInfo info = X509Utils.info(parsed);
        assertEquals(certificate.getSubjectX500Principal().getName(), info.subject());
        assertEquals(info.subject(), info.issuer());
        assertEquals(3, info.version());
        assertEquals("SHA256WITHRSA", info.signatureAlgorithm());
        assertEquals("RSA", info.publicKeyAlgorithm());
        assertTrue(info.isValidAt(Instant.now()));
        assertFalse(info.isExpired());
        assertTrue(info.isSelfSigned());
        assertFalse(info.certificateAuthority());
        assertEquals(List.of("dns:modality.example.com", "ip:127.0.0.1"), info.subjectAlternativeNames());
        assertEquals(64, X509Utils.fingerprint(parsed).length());
        assertTrue(X509Utils.isValid(parsed));
        assertTrue(X509Utils.isSelfSigned(parsed));
        assertFalse(X509Utils.isCertificateAuthority(parsed));
    }

    @Test
    void detectsExpiredCertificates() {
        KeyPair keyPair = EcdsaUtils.generateKeyPair(EcCurve.P_256);
        X509Certificate expired = CertificateGenerator.selfSigned(keyPair, CertificateSpec.of("CN=expired", Duration.ofSeconds(-30)));
        assertFalse(X509Utils.isValid(expired));
        assertTrue(X509Utils.info(expired).isExpired());
        assertFalse(X509Utils.info(expired).isValidAt(Instant.now()));
    }

    @Test
    void verifiesSignaturesAndChains() {
        KeyPair caKeyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_2048);
        X509Certificate ca = CertificateGenerator.selfSignedCa(caKeyPair, "CN=modality-ca,O=slainc", Duration.ofDays(3650));
        assertTrue(X509Utils.isCertificateAuthority(ca));
        KeyPair leafKeyPair = EcdsaUtils.generateKeyPair(EcCurve.P_256);
        X509Certificate leaf = CertificateGenerator.issue(caKeyPair, ca, leafKeyPair.getPublic(), LEAF_SPEC);
        assertTrue(X509Utils.verify(leaf, ca.getPublicKey()));
        assertFalse(X509Utils.verify(leaf, leafKeyPair.getPublic()));
        assertEquals(ca.getSubjectX500Principal(), leaf.getIssuerX500Principal());
        X509Utils.validateChain(List.of(leaf, ca));
        assertTrue(X509Utils.isChainValid(List.of(leaf, ca)));
        KeyPair otherCaKeyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_2048);
        X509Certificate otherCa = CertificateGenerator.selfSignedCa(otherCaKeyPair, "CN=other-ca", Duration.ofDays(365));
        assertFalse(X509Utils.isChainValid(List.of(leaf, otherCa)));
        assertThrows(CryptoException.class, () -> X509Utils.validateChain(List.of()));
        assertThrows(CryptoException.class, () -> X509Utils.validateChain(List.of(leaf, otherCa)));
    }

    @Test
    void validatesSingleCertificateChain() {
        KeyPair keyPair = EcdsaUtils.generateKeyPair(EcCurve.P_256);
        X509Certificate certificate = CertificateGenerator.selfSigned(keyPair, LEAF_SPEC);
        X509Utils.validateChain(List.of(certificate));
        X509Certificate expired = CertificateGenerator.selfSigned(keyPair, CertificateSpec.of("CN=expired", Duration.ofSeconds(-5)));
        assertThrows(CryptoException.class, () -> X509Utils.validateChain(List.of(expired)));
    }

    @Test
    void parsesCertificateBundles() {
        KeyPair keyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_1024);
        X509Certificate first = CertificateGenerator.selfSigned(keyPair, CertificateSpec.of("CN=one", Duration.ofDays(1)));
        X509Certificate second = CertificateGenerator.selfSigned(keyPair, CertificateSpec.of("CN=two", Duration.ofDays(1)));
        List<X509Certificate> certificates = X509Utils.parseAll(X509Utils.toPem(first) + X509Utils.toPem(second));
        assertEquals(2, certificates.size());
        assertEquals("CN=one", certificates.get(0).getSubjectX500Principal().getName());
        assertThrows(CryptoException.class, () -> X509Utils.parsePem(X509Utils.toPem(first).replace("CERTIFICATE", "PRIVATE KEY")));
        assertThrows(CryptoException.class, () -> X509Utils.parse(new byte[0]));
    }
}
