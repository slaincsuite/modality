package cn.slainc.crypto.key;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.CryptoProviders;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemObjectGenerator;
import org.bouncycastle.util.io.pem.PemWriter;

public final class PemUtils {

    public static final String CERTIFICATE_TYPE = "CERTIFICATE";

    public static final String PRIVATE_KEY_TYPE = "PRIVATE KEY";

    public static final String PUBLIC_KEY_TYPE = "PUBLIC KEY";

    public static final String CERTIFICATE_REQUEST_TYPE = "CERTIFICATE REQUEST";

    private PemUtils() {
    }

    public static String encode(Object object) {
        try (StringWriter writer = new StringWriter(); JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(object);
            pemWriter.flush();
            return writer.toString();
        } catch (IOException exception) {
            throw new CryptoException("无法编码 PEM 对象", exception);
        }
    }

    public static String encodePrivateKey(PrivateKey privateKey) {
        return write(new PemObject(PRIVATE_KEY_TYPE, privateKey.getEncoded()));
    }

    public static String encodePublicKey(PublicKey publicKey) {
        return write(new PemObject(PUBLIC_KEY_TYPE, publicKey.getEncoded()));
    }

    public static String encodeCertificate(X509Certificate certificate) {
        return encode(certificate);
    }

    public static String encodeCertificationRequest(PKCS10CertificationRequest request) {
        return encode(request);
    }

    public static String write(PemObjectGenerator generator) {
        try (StringWriter writer = new StringWriter(); PemWriter pemWriter = new PemWriter(writer)) {
            pemWriter.writeObject(generator);
            pemWriter.flush();
            return writer.toString();
        } catch (IOException exception) {
            throw new CryptoException("无法编码 PEM 对象", exception);
        }
    }

    public static Object decode(String pem) {
        List<Object> objects = decodeAll(pem);
        if (objects.isEmpty()) {
            throw new CryptoException("PEM 文本中不包含对象");
        }
        return objects.get(0);
    }

    public static List<Object> decodeAll(String pem) {
        if (pem == null) {
            throw new CryptoException("PEM 文本不能为空");
        }
        List<Object> objects = new ArrayList<>();
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object object;
            while ((object = parser.readObject()) != null) {
                objects.add(convert(object));
            }
        } catch (IOException exception) {
            throw new CryptoException("无法读取 PEM 文本", exception);
        }
        return objects;
    }

    public static PrivateKey readPrivateKey(String pem) {
        Object object = decode(pem);
        if (object instanceof PrivateKey privateKey) {
            return privateKey;
        }
        if (object instanceof KeyPair keyPair) {
            return keyPair.getPrivate();
        }
        throw new CryptoException("PEM 文本中不包含私钥：" + object.getClass().getName());
    }

    public static PublicKey readPublicKey(String pem) {
        Object object = decode(pem);
        if (object instanceof PublicKey publicKey) {
            return publicKey;
        }
        if (object instanceof KeyPair keyPair) {
            return keyPair.getPublic();
        }
        if (object instanceof X509Certificate certificate) {
            return certificate.getPublicKey();
        }
        throw new CryptoException("PEM 文本中不包含公钥：" + object.getClass().getName());
    }

    public static X509Certificate readCertificate(String pem) {
        Object object = decode(pem);
        if (object instanceof X509Certificate certificate) {
            return certificate;
        }
        throw new CryptoException("PEM 文本中不包含证书：" + object.getClass().getName());
    }

    public static PKCS10CertificationRequest readCertificationRequest(String pem) {
        Object object = decode(pem);
        if (object instanceof PKCS10CertificationRequest request) {
            return request;
        }
        throw new CryptoException("PEM 文本中不包含证书请求：" + object.getClass().getName());
    }

    public static PrivateKey privateKey(byte[] pkcs8) {
        try {
            return converter().getPrivateKey(PrivateKeyInfo.getInstance(pkcs8));
        } catch (IOException | RuntimeException exception) {
            throw new CryptoException("无法读取 PKCS#8 私钥", exception);
        }
    }

    public static PublicKey publicKey(byte[] x509) {
        try {
            return converter().getPublicKey(SubjectPublicKeyInfo.getInstance(x509));
        } catch (IOException | RuntimeException exception) {
            throw new CryptoException("无法读取 X.509 公钥", exception);
        }
    }

    public static X509Certificate certificate(byte[] der) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509", CryptoProviders.bouncyCastle());
            return (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(der));
        } catch (CertificateException exception) {
            throw new CryptoException("无法读取 X.509 证书", exception);
        }
    }

    public static byte[] encodeDer(Object object) {
        if (object instanceof PrivateKey privateKey) {
            return privateKey.getEncoded();
        }
        if (object instanceof PublicKey publicKey) {
            return publicKey.getEncoded();
        }
        if (object instanceof X509Certificate certificate) {
            try {
                return certificate.getEncoded();
            } catch (CertificateException exception) {
                throw new CryptoException("无法编码证书", exception);
            }
        }
        if (object instanceof PKCS10CertificationRequest request) {
            try {
                return request.getEncoded();
            } catch (IOException exception) {
                throw new CryptoException("无法编码证书请求", exception);
            }
        }
        throw new CryptoException("不支持的 DER 对象：" + object.getClass().getName());
    }

    public static String body(String pem) {
        if (pem == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (String line : pem.split("\\R")) {
            if (!line.startsWith("-----")) {
                builder.append(line.trim());
            }
        }
        return builder.toString();
    }

    private static Object convert(Object object) {
        try {
            if (object instanceof PrivateKeyInfo privateKeyInfo) {
                return converter().getPrivateKey(privateKeyInfo);
            }
            if (object instanceof PEMKeyPair keyPair) {
                return converter().getKeyPair(keyPair);
            }
            if (object instanceof SubjectPublicKeyInfo publicKeyInfo) {
                return converter().getPublicKey(publicKeyInfo);
            }
            if (object instanceof X509CertificateHolder holder) {
                return new JcaX509CertificateConverter().setProvider(CryptoProviders.bouncyCastle()).getCertificate(holder);
            }
            if (object instanceof PrivateKey || object instanceof PublicKey || object instanceof PKCS10CertificationRequest) {
                return object;
            }
            throw new CryptoException("不支持的 PEM 对象：" + object.getClass().getName());
        } catch (IOException | CertificateException exception) {
            throw new CryptoException("无法转换 PEM 对象", exception);
        }
    }

    private static JcaPEMKeyConverter converter() {
        return new JcaPEMKeyConverter().setProvider(CryptoProviders.bouncyCastle());
    }
}
