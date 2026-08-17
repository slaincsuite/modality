package cn.slainc.crypto.sign;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.asymmetric.EcdsaSignatureAlgorithm;
import cn.slainc.crypto.asymmetric.EdDsaAlgorithm;
import cn.slainc.crypto.asymmetric.JcaSignatures;
import cn.slainc.crypto.sm.Sm2Utils;
import java.math.BigInteger;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECKey;
import java.security.interfaces.EdECKey;

public final class SignUtils {

    private static final BigInteger SM2_ORDER = new BigInteger("FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFF7203DF6B21C6052B53BBF40939D54123", 16);

    private SignUtils() {
    }

    public static byte[] sign(SignatureAlgorithm algorithm, PrivateKey privateKey, byte[] data) {
        if (algorithm.isSm2()) {
            return Sm2Utils.signDer(privateKey, data);
        }
        return JcaSignatures.sign(algorithm.jcaName(), algorithm.pssDigest(), algorithm.pssSaltLength(), privateKey, data);
    }

    public static boolean verify(SignatureAlgorithm algorithm, PublicKey publicKey, byte[] data, byte[] signature) {
        if (algorithm.isSm2()) {
            return Sm2Utils.verifyDer(publicKey, data, signature);
        }
        return JcaSignatures.verify(algorithm.jcaName(), algorithm.pssDigest(), algorithm.pssSaltLength(), publicKey, data, signature);
    }

    public static byte[] sign(PrivateKey privateKey, byte[] data) {
        return sign(detect(privateKey), privateKey, data);
    }

    public static boolean verify(PublicKey publicKey, byte[] data, byte[] signature) {
        return verify(detect(publicKey), publicKey, data, signature);
    }

    public static String signBase64(SignatureAlgorithm algorithm, PrivateKey privateKey, byte[] data) {
        return Base64Utils.encode(sign(algorithm, privateKey, data));
    }

    public static boolean verifyBase64(SignatureAlgorithm algorithm, PublicKey publicKey, byte[] data, String signature) {
        return verify(algorithm, publicKey, data, Base64Utils.decode(signature));
    }

    public static boolean verifyQuietly(SignatureAlgorithm algorithm, PublicKey publicKey, byte[] data, byte[] signature) {
        try {
            return verify(algorithm, publicKey, data, signature);
        } catch (CryptoException exception) {
            return false;
        }
    }

    public static SignatureAlgorithm detect(Key key) {
        String algorithm = key.getAlgorithm();
        if ("RSA".equals(algorithm) || algorithm.startsWith("RSA")) {
            return SignatureAlgorithm.SHA256_WITH_RSA;
        }
        if (key instanceof ECKey ecKey) {
            if (SM2_ORDER.equals(ecKey.getParams().getOrder())) {
                return SignatureAlgorithm.SM3_WITH_SM2;
            }
            int fieldSize = ecKey.getParams().getCurve().getField().getFieldSize();
            if (fieldSize <= 256) {
                return SignatureAlgorithm.SHA256_WITH_ECDSA;
            }
            return fieldSize <= 384 ? SignatureAlgorithm.SHA384_WITH_ECDSA : SignatureAlgorithm.SHA512_WITH_ECDSA;
        }
        if (key instanceof EdECKey edKey) {
            return "Ed448".equals(edKey.getParams().getName()) ? SignatureAlgorithm.ED448 : SignatureAlgorithm.ED25519;
        }
        if ("Ed25519".equals(algorithm)) {
            return SignatureAlgorithm.ED25519;
        }
        if ("Ed448".equals(algorithm)) {
            return SignatureAlgorithm.ED448;
        }
        if ("EdDSA".equals(algorithm)) {
            return SignatureAlgorithm.ED25519;
        }
        if ("SM2".equals(algorithm)) {
            return SignatureAlgorithm.SM3_WITH_SM2;
        }
        throw new CryptoException("不支持的密钥算法：" + algorithm);
    }

    public static EcdsaSignatureAlgorithm ecdsa(SignatureAlgorithm algorithm) {
        return switch (algorithm) {
            case SHA384_WITH_ECDSA -> EcdsaSignatureAlgorithm.SHA384_WITH_ECDSA;
            case SHA512_WITH_ECDSA -> EcdsaSignatureAlgorithm.SHA512_WITH_ECDSA;
            default -> EcdsaSignatureAlgorithm.SHA256_WITH_ECDSA;
        };
    }

    public static EdDsaAlgorithm eddsa(SignatureAlgorithm algorithm) {
        return algorithm == SignatureAlgorithm.ED448 ? EdDsaAlgorithm.ED448 : EdDsaAlgorithm.ED25519;
    }
}
