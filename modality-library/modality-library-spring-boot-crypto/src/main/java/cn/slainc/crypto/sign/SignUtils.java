package cn.slainc.crypto.sign;

import cn.slainc.crypto.CryptoException;
import java.math.BigInteger;
import java.security.Key;
import java.security.interfaces.ECKey;
import java.security.interfaces.EdECKey;

public final class SignUtils {

    private static final BigInteger SM2_ORDER = new BigInteger("FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFF7203DF6B21C6052B53BBF40939D54123", 16);

    private SignUtils() {
    }

    public static SignatureAlgorithm detect(Key key) {
        String algorithm = key.getAlgorithm();
        if (algorithm.startsWith("RSA")) {
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
}
