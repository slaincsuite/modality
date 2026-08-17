package cn.slainc.crypto.cert;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.CryptoProviders;
import cn.slainc.crypto.key.PemUtils;
import cn.slainc.crypto.random.SecureRandomUtils;
import cn.slainc.crypto.sign.SignatureAlgorithm;
import cn.slainc.crypto.sign.SignUtils;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.PKCSException;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

public final class CsrUtils {

    private CsrUtils() {
    }

    public static PKCS10CertificationRequest generate(KeyPair keyPair, String subject) {
        return generate(keyPair, subject, SignUtils.detect(keyPair.getPrivate()));
    }

    public static PKCS10CertificationRequest generateSm2(KeyPair keyPair, String subject) {
        return generate(keyPair, subject, SignatureAlgorithm.SM3_WITH_SM2);
    }

    public static PKCS10CertificationRequest generate(KeyPair keyPair, String subject, SignatureAlgorithm algorithm) {
        return generate(keyPair, subject, algorithm, List.of());
    }

    public static PKCS10CertificationRequest generate(KeyPair keyPair, String subject, SignatureAlgorithm algorithm, List<String> subjectAlternativeNames) {
        try {
            PKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(new X500Name(dn(subject)), keyPair.getPublic());
            if (subjectAlternativeNames != null && !subjectAlternativeNames.isEmpty()) {
                builder = builder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensions(subjectAlternativeNames));
            }
            ContentSigner signer = new JcaContentSignerBuilder(algorithm.jcaName())
                    .setProvider(CryptoProviders.bouncyCastle())
                    .setSecureRandom(SecureRandomUtils.secureRandom())
                    .build(keyPair.getPrivate());
            return builder.build(signer);
        } catch (OperatorCreationException | IOException exception) {
            throw new CryptoException("生成 PKCS#10 证书请求失败", exception);
        }
    }

    public static byte[] encode(PKCS10CertificationRequest request) {
        try {
            return request.getEncoded();
        } catch (IOException exception) {
            throw new CryptoException("编码 PKCS#10 证书请求失败", exception);
        }
    }

    public static String toPem(PKCS10CertificationRequest request) {
        return PemUtils.encodeCertificationRequest(request);
    }

    public static PKCS10CertificationRequest parse(byte[] encoded) {
        try {
            return new PKCS10CertificationRequest(encoded);
        } catch (IOException exception) {
            throw new CryptoException("解析 PKCS#10 证书请求失败", exception);
        }
    }

    public static PKCS10CertificationRequest parsePem(String pem) {
        return PemUtils.readCertificationRequest(pem);
    }

    public static PublicKey publicKey(PKCS10CertificationRequest request) {
        try {
            return new JcaPKCS10CertificationRequest(request).setProvider(CryptoProviders.bouncyCastle()).getPublicKey();
        } catch (Exception exception) {
            throw new CryptoException("读取证书请求公钥失败", exception);
        }
    }

    public static String subject(PKCS10CertificationRequest request) {
        return request.getSubject().toString();
    }

    public static boolean verify(PKCS10CertificationRequest request) {
        try {
            PublicKey publicKey = publicKey(request);
            return request.isSignatureValid(new JcaContentVerifierProviderBuilder().setProvider(CryptoProviders.bouncyCastle()).build(publicKey));
        } catch (OperatorCreationException | PKCSException exception) {
            throw new CryptoException("校验证书请求签名失败", exception);
        }
    }

    public static boolean verifyQuietly(PKCS10CertificationRequest request) {
        try {
            return verify(request);
        } catch (CryptoException exception) {
            return false;
        }
    }

    private static Extensions extensions(List<String> names) throws IOException {
        ASN1EncodableVector vector = new ASN1EncodableVector();
        for (String name : names) {
            vector.add(new GeneralName(GeneralName.dNSName, name));
        }
        Extension extension = new Extension(Extension.subjectAlternativeName, false, new DERSequence(vector).getEncoded());
        return new Extensions(extension);
    }

    private static String dn(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new CryptoException("证书主体不能为空");
        }
        return subject.contains("=") ? subject : "CN=" + subject;
    }
}
