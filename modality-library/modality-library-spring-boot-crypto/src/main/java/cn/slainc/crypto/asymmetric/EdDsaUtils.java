package cn.slainc.crypto.asymmetric;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.CryptoProviders;
import cn.slainc.crypto.random.SecureRandomUtils;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.NamedParameterSpec;

public final class EdDsaUtils {

    private EdDsaUtils() {
    }

    public static KeyPair generateKeyPair(EdDsaAlgorithm algorithm) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm.jcaName(), CryptoProviders.bouncyCastle());
            generator.initialize(new NamedParameterSpec(algorithm.jcaName()), SecureRandomUtils.secureRandom());
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("无法生成 " + algorithm + " 密钥对", exception);
        }
    }

    public static byte[] sign(EdDsaAlgorithm algorithm, PrivateKey privateKey, byte[] data) {
        return JcaSignatures.sign(algorithm.jcaName(), null, 0, privateKey, data);
    }

    public static boolean verify(EdDsaAlgorithm algorithm, PublicKey publicKey, byte[] data, byte[] signature) {
        return JcaSignatures.verify(algorithm.jcaName(), null, 0, publicKey, data, signature);
    }
}
