package cn.slainc.crypto.asymmetric;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.CryptoProviders;
import cn.slainc.crypto.random.SecureRandomUtils;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECKey;
import java.security.spec.ECGenParameterSpec;

public final class EcdsaUtils {

    private EcdsaUtils() {
    }

    public static KeyPair generateKeyPair(EcCurve curve) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", CryptoProviders.bouncyCastle());
            generator.initialize(new ECGenParameterSpec(curve.curveName()), SecureRandomUtils.secureRandom());
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("无法生成 EC 密钥对", exception);
        }
    }

    public static byte[] sign(PrivateKey privateKey, byte[] data) {
        return sign(defaultAlgorithm(privateKey), privateKey, data);
    }

    public static byte[] sign(EcdsaSignatureAlgorithm algorithm, PrivateKey privateKey, byte[] data) {
        return JcaSignatures.sign(algorithm.jcaName(), null, 0, privateKey, data);
    }

    public static boolean verify(PublicKey publicKey, byte[] data, byte[] signature) {
        return verify(defaultAlgorithm(publicKey), publicKey, data, signature);
    }

    public static boolean verify(EcdsaSignatureAlgorithm algorithm, PublicKey publicKey, byte[] data, byte[] signature) {
        return JcaSignatures.verify(algorithm.jcaName(), null, 0, publicKey, data, signature);
    }

    public static EcdsaSignatureAlgorithm defaultAlgorithm(Key key) {
        if (!(key instanceof ECKey ecKey)) {
            throw new CryptoException("不是 EC 密钥：" + key.getAlgorithm());
        }
        return EcdsaSignatureAlgorithm.forFieldSize(ecKey.getParams().getCurve().getField().getFieldSize());
    }
}
