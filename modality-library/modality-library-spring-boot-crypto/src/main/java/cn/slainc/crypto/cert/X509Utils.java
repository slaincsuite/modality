package cn.slainc.crypto.cert;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.CryptoProviders;
import cn.slainc.crypto.codec.HexUtils;
import cn.slainc.crypto.digest.DigestAlgorithm;
import cn.slainc.crypto.digest.DigestUtils;
import cn.slainc.crypto.key.PemUtils;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class X509Utils {

    private X509Utils() {
    }

    public static X509Certificate parse(byte[] encoded) {
        if (encoded == null || encoded.length == 0) {
            throw new CryptoException("证书数据不能为空");
        }
        String text = new String(encoded, StandardCharsets.ISO_8859_1);
        if (text.contains("-----BEGIN")) {
            return parsePem(text);
        }
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509", CryptoProviders.bouncyCastle());
            return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(encoded));
        } catch (CertificateException exception) {
            throw new CryptoException("解析 X.509 证书失败", exception);
        }
    }

    public static X509Certificate parsePem(String pem) {
        return PemUtils.readCertificate(pem);
    }

    public static List<X509Certificate> parseAll(String pem) {
        List<X509Certificate> certificates = new ArrayList<>();
        for (Object object : PemUtils.decodeAll(pem)) {
            if (object instanceof X509Certificate certificate) {
                certificates.add(certificate);
            }
        }
        if (certificates.isEmpty()) {
            throw new CryptoException("PEM 文本中不包含证书");
        }
        return certificates;
    }

    public static CertificateInfo info(X509Certificate certificate) {
        return new CertificateInfo(
                certificate.getSubjectX500Principal().getName(),
                certificate.getIssuerX500Principal().getName(),
                certificate.getSerialNumber(),
                certificate.getNotBefore().toInstant(),
                certificate.getNotAfter().toInstant(),
                certificate.getSigAlgName(),
                certificate.getPublicKey().getAlgorithm(),
                certificate.getVersion(),
                isCertificateAuthority(certificate),
                subjectAlternativeNames(certificate));
    }

    public static boolean isValid(X509Certificate certificate) {
        return isValid(certificate, Instant.now());
    }

    public static boolean isValid(X509Certificate certificate, Instant instant) {
        try {
            certificate.checkValidity(java.util.Date.from(instant));
            return true;
        } catch (CertificateException exception) {
            return false;
        }
    }

    public static boolean verify(X509Certificate certificate, PublicKey issuerPublicKey) {
        try {
            certificate.verify(issuerPublicKey, CryptoProviders.bouncyCastle());
            return true;
        } catch (GeneralSecurityException exception) {
            return false;
        }
    }

    public static boolean isSelfSigned(X509Certificate certificate) {
        return certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal()) && verify(certificate, certificate.getPublicKey());
    }

    public static boolean isCertificateAuthority(X509Certificate certificate) {
        return certificate.getBasicConstraints() >= 0;
    }

    public static void validateChain(List<X509Certificate> chain) {
        if (chain == null || chain.isEmpty()) {
            throw new CryptoException("证书链不能为空");
        }
        List<X509Certificate> certificates = List.copyOf(chain);
        X509Certificate trustAnchorCertificate = certificates.get(certificates.size() - 1);
        if (certificates.size() == 1) {
            if (!isValid(trustAnchorCertificate)) {
                throw new CryptoException("证书不在有效期内");
            }
            return;
        }
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509", CryptoProviders.bouncyCastle());
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            PKIXParameters parameters = new PKIXParameters(Set.of(new TrustAnchor(trustAnchorCertificate, null)));
            parameters.setRevocationEnabled(false);
            validator.validate(factory.generateCertPath(certificates.subList(0, certificates.size() - 1)), parameters);
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("证书链校验失败", exception);
        }
    }

    public static boolean isChainValid(List<X509Certificate> chain) {
        try {
            validateChain(chain);
            return true;
        } catch (CryptoException exception) {
            return false;
        }
    }

    public static List<String> subjectAlternativeNames(X509Certificate certificate) {
        List<String> names = new ArrayList<>();
        try {
            Collection<List<?>> entries = certificate.getSubjectAlternativeNames();
            if (entries == null) {
                return names;
            }
            for (List<?> entry : entries) {
                if (entry.size() >= 2 && entry.get(0) instanceof Integer type) {
                    names.add(name(type, String.valueOf(entry.get(1))));
                }
            }
        } catch (CertificateException exception) {
            throw new CryptoException("读取证书扩展失败", exception);
        }
        return names;
    }

    public static String fingerprint(X509Certificate certificate) {
        return fingerprint(certificate, DigestAlgorithm.SHA256);
    }

    public static String fingerprint(X509Certificate certificate, DigestAlgorithm algorithm) {
        try {
            return HexUtils.encode(DigestUtils.digest(algorithm, certificate.getEncoded()));
        } catch (CertificateException exception) {
            throw new CryptoException("读取证书编码失败", exception);
        }
    }

    public static String toPem(X509Certificate certificate) {
        return PemUtils.encodeCertificate(certificate);
    }

    private static String name(int type, String value) {
        return switch (type) {
            case 1 -> "email:" + value;
            case 2 -> "dns:" + value;
            case 6 -> "uri:" + value;
            case 7 -> "ip:" + value;
            default -> type + ":" + value;
        };
    }
}
