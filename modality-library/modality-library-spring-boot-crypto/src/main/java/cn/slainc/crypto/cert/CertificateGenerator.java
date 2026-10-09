package cn.slainc.crypto.cert;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.CryptoProviders;
import cn.slainc.crypto.key.PemUtils;
import cn.slainc.crypto.random.SecureRandomUtils;
import cn.slainc.crypto.sign.SignUtils;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public final class CertificateGenerator {

    private static final BigInteger SERIAL_LIMIT = BigInteger.valueOf(Long.MAX_VALUE);

    private CertificateGenerator() {
    }

    public static X509Certificate selfSigned(KeyPair keyPair, CertificateSpec spec) {
        return build(keyPair.getPrivate(), keyPair.getPublic(), subject(spec.subject()), subject(spec.subject()), spec, null);
    }

    public static X509Certificate selfSignedCa(KeyPair keyPair, String subject, Duration validity) {
        CertificateSpec spec = CertificateSpec.of(subject, validity).asCertificateAuthority(-1)
                .withKeyUsages("keyCertSign", "cRLSign", "digitalSignature");
        return selfSigned(keyPair, spec);
    }

    public static X509Certificate issue(KeyPair issuerKeyPair, X509Certificate issuerCertificate, PublicKey subjectPublicKey, CertificateSpec spec) {
        return issue(issuerKeyPair.getPrivate(), issuerCertificate, subjectPublicKey, spec);
    }

    public static X509Certificate issue(PrivateKey issuerPrivateKey, X509Certificate issuerCertificate, PublicKey subjectPublicKey, CertificateSpec spec) {
        if (issuerCertificate == null) {
            throw new CryptoException("签发者证书不能为空");
        }
        X500Name issuer = X500Name.getInstance(issuerCertificate.getSubjectX500Principal().getEncoded());
        return build(issuerPrivateKey, subjectPublicKey, issuer, subject(spec.subject()), spec, issuerCertificate);
    }

    public static String toPem(X509Certificate certificate) {
        return PemUtils.encodeCertificate(certificate);
    }

    private static X509Certificate build(PrivateKey signerKey, PublicKey subjectPublicKey, X500Name issuer, X500Name subject, CertificateSpec spec, X509Certificate issuerCertificate) {
        Instant now = Instant.now();
        Date notBefore = Date.from(now.minus(Duration.ofMinutes(5)));
        Date notAfter = Date.from(now.plus(spec.validity()));
        BigInteger serial = spec.serial() != null ? spec.serial() : randomSerial();
        try {
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuer, serial, notBefore, notAfter, subject, subjectPublicKey);
            JcaX509ExtensionUtils extensions = new JcaX509ExtensionUtils();
            builder.addExtension(Extension.subjectKeyIdentifier, false, extensions.createSubjectKeyIdentifier(subjectPublicKey));
            if (issuerCertificate != null) {
                builder.addExtension(Extension.authorityKeyIdentifier, false, extensions.createAuthorityKeyIdentifier(issuerCertificate.getPublicKey()));
            }
            builder.addExtension(Extension.basicConstraints, true, basicConstraints(spec));
            builder.addExtension(Extension.keyUsage, true, keyUsage(spec));
            if (!spec.subjectAlternativeNames().isEmpty()) {
                builder.addExtension(Extension.subjectAlternativeName, false, generalNames(spec.subjectAlternativeNames()));
            }
            if (!spec.extendedKeyUsages().isEmpty()) {
                builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(extendedKeyPurposes(spec.extendedKeyUsages())));
            }
            ContentSigner signer = new JcaContentSignerBuilder(SignUtils.detect(signerKey).jcaName())
                    .setProvider(CryptoProviders.bouncyCastle())
                    .setSecureRandom(SecureRandomUtils.secureRandom())
                    .build(signerKey);
            X509CertificateHolder holder = builder.build(signer);
            return new JcaX509CertificateConverter().setProvider(CryptoProviders.bouncyCastle()).getCertificate(holder);
        } catch (OperatorCreationException | java.security.cert.CertificateException | java.io.IOException | java.security.NoSuchAlgorithmException exception) {
            throw new CryptoException("生成 X.509 证书失败", exception);
        }
    }

    private static BasicConstraints basicConstraints(CertificateSpec spec) {
        if (!spec.certificateAuthority()) {
            return new BasicConstraints(false);
        }
        return spec.pathLength() >= 0 ? new BasicConstraints(spec.pathLength()) : new BasicConstraints(true);
    }

    private static BigInteger randomSerial() {
        BigInteger serial;
        do {
            serial = new BigInteger(64, SecureRandomUtils.secureRandom());
        } while (serial.signum() <= 0 || serial.compareTo(SERIAL_LIMIT) > 0);
        return serial;
    }

    private static X500Name subject(String value) {
        return new X500Name(value.contains("=") ? value : "CN=" + value);
    }

    private static KeyUsage keyUsage(CertificateSpec spec) {
        List<String> usages = spec.keyUsages();
        int bits = 0;
        if (usages.isEmpty()) {
            bits = spec.certificateAuthority() ? KeyUsage.keyCertSign | KeyUsage.cRLSign | KeyUsage.digitalSignature : KeyUsage.digitalSignature | KeyUsage.keyEncipherment;
        } else {
            for (String usage : usages) {
                bits |= switch (usage) {
                    case "digitalSignature" -> KeyUsage.digitalSignature;
                    case "nonRepudiation", "contentCommitment" -> KeyUsage.nonRepudiation;
                    case "keyEncipherment" -> KeyUsage.keyEncipherment;
                    case "dataEncipherment" -> KeyUsage.dataEncipherment;
                    case "keyAgreement" -> KeyUsage.keyAgreement;
                    case "keyCertSign" -> KeyUsage.keyCertSign;
                    case "cRLSign" -> KeyUsage.cRLSign;
                    case "encipherOnly" -> KeyUsage.encipherOnly;
                    case "decipherOnly" -> KeyUsage.decipherOnly;
                    default -> throw new CryptoException("不支持的关键字用途：" + usage);
                };
            }
        }
        return new KeyUsage(bits);
    }

    private static KeyPurposeId[] extendedKeyPurposes(List<String> usages) {
        KeyPurposeId[] purposes = new KeyPurposeId[usages.size()];
        for (int i = 0; i < usages.size(); i++) {
            purposes[i] = switch (usages.get(i)) {
                case "serverAuth" -> KeyPurposeId.id_kp_serverAuth;
                case "clientAuth" -> KeyPurposeId.id_kp_clientAuth;
                case "codeSigning" -> KeyPurposeId.id_kp_codeSigning;
                case "emailProtection" -> KeyPurposeId.id_kp_emailProtection;
                case "timeStamping" -> KeyPurposeId.id_kp_timeStamping;
                case "ocspSigning" -> KeyPurposeId.id_kp_OCSPSigning;
                default -> throw new CryptoException("不支持的扩展关键字用途：" + usages.get(i));
            };
        }
        return purposes;
    }

    private static GeneralNames generalNames(List<String> names) {
        GeneralName[] generalNames = new GeneralName[names.size()];
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            generalNames[i] = isIpAddress(name) ? new GeneralName(GeneralName.iPAddress, name) : new GeneralName(GeneralName.dNSName, name);
        }
        return new GeneralNames(generalNames);
    }

    private static boolean isIpAddress(String value) {
        try {
            InetAddress.getByName(value);
            return value.indexOf(':') >= 0 || value.chars().allMatch(character -> Character.isDigit(character) || character == '.');
        } catch (Exception exception) {
            return false;
        }
    }
}
