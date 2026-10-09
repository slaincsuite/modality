package cn.slainc.crypto.asymmetric;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.CryptoProviders;
import cn.slainc.crypto.random.SecureRandomUtils;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public final class RsaUtils {

    private RsaUtils() {
    }

    public static KeyPair generateKeyPair(RsaKeySize keySize) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", CryptoProviders.bouncyCastle());
            generator.initialize(keySize.bits(), SecureRandomUtils.secureRandom());
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("无法生成 RSA 密钥对", exception);
        }
    }

    public static byte[] encrypt(RsaTransformation transformation, PublicKey publicKey, byte[] data) {
        requirePlainTextLength(transformation, publicKey, data);
        return cipher(transformation, Cipher.ENCRYPT_MODE, publicKey, data);
    }

    public static byte[] decrypt(RsaTransformation transformation, PrivateKey privateKey, byte[] data) {
        return cipher(transformation, Cipher.DECRYPT_MODE, privateKey, data);
    }

    public static byte[] sign(RsaSignatureAlgorithm algorithm, PrivateKey privateKey, byte[] data) {
        return JcaSignatures.sign(algorithm.jcaName(), algorithm.pssDigest(), algorithm.pssSaltLength(), privateKey, data);
    }

    public static boolean verify(RsaSignatureAlgorithm algorithm, PublicKey publicKey, byte[] data, byte[] signature) {
        return JcaSignatures.verify(algorithm.jcaName(), algorithm.pssDigest(), algorithm.pssSaltLength(), publicKey, data, signature);
    }

    private static byte[] cipher(RsaTransformation transformation, int mode, java.security.Key key, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(transformation.transformation(), CryptoProviders.bouncyCastle());
            OAEPParameterSpec spec = oaepSpec(transformation);
            if (spec == null) {
                cipher.init(mode, key, SecureRandomUtils.secureRandom());
            } else {
                cipher.init(mode, key, spec, SecureRandomUtils.secureRandom());
            }
            return cipher.doFinal(data);
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("RSA 数据处理失败，转换方式：" + transformation, exception);
        } catch (RuntimeException exception) {
            throw new CryptoException("RSA 数据处理失败，转换方式：" + transformation, exception);
        }
    }

    private static void requirePlainTextLength(RsaTransformation transformation, PublicKey publicKey, byte[] data) {
        if (!(publicKey instanceof RSAPublicKey rsaPublicKey) || data == null) {
            return;
        }
        int modulus = (rsaPublicKey.getModulus().bitLength() + 7) / 8;
        int maximum = transformation.isOaep() ? modulus - 2 * digestLength(transformation.digest()) - 2 : modulus - 11;
        if (data.length > maximum) {
            throw new CryptoException("明文长度 " + data.length + " 字节超出 " + transformation + " 允许的上限 " + maximum + " 字节");
        }
    }

    private static int digestLength(String digest) {
        return switch (digest) {
            case "SHA-1" -> 20;
            case "SHA-256" -> 32;
            case "SHA-384" -> 48;
            default -> 64;
        };
    }

    private static OAEPParameterSpec oaepSpec(RsaTransformation transformation) {
        if (!transformation.isOaep()) {
            return null;
        }
        return new OAEPParameterSpec(transformation.digest(), "MGF1", new MGF1ParameterSpec(transformation.digest()), PSource.PSpecified.DEFAULT);
    }
}
