package cn.slainc.crypto.jwt;

import cn.slainc.crypto.CryptoException;
import java.security.Key;
import java.security.interfaces.ECKey;
import java.security.interfaces.EdECKey;
import java.security.interfaces.RSAPublicKey;
import javax.crypto.SecretKey;

public enum JwtAlgorithm {

    HS256("HS256", "HmacSHA256", JwtKeyType.MAC, null, 0, 0),
    HS384("HS384", "HmacSHA384", JwtKeyType.MAC, null, 0, 0),
    HS512("HS512", "HmacSHA512", JwtKeyType.MAC, null, 0, 0),
    RS256("RS256", "SHA256withRSA", JwtKeyType.RSA, null, 0, 0),
    RS384("RS384", "SHA384withRSA", JwtKeyType.RSA, null, 0, 0),
    RS512("RS512", "SHA512withRSA", JwtKeyType.RSA, null, 0, 0),
    PS256("PS256", "RSASSA-PSS", JwtKeyType.RSA, "SHA-256", 32, 0),
    PS384("PS384", "RSASSA-PSS", JwtKeyType.RSA, "SHA-384", 48, 0),
    PS512("PS512", "RSASSA-PSS", JwtKeyType.RSA, "SHA-512", 64, 0),
    ES256("ES256", "SHA256withECDSA", JwtKeyType.EC, null, 0, 256),
    ES384("ES384", "SHA384withECDSA", JwtKeyType.EC, null, 0, 384),
    ES512("ES512", "SHA512withECDSA", JwtKeyType.EC, null, 0, 521),
    EdDSA("EdDSA", "Ed25519", JwtKeyType.OKP, null, 0, 0);

    private final String alg;

    private final String jcaName;

    private final JwtKeyType keyType;

    private final String digest;

    private final int saltLength;

    private final int fieldSize;

    JwtAlgorithm(String alg, String jcaName, JwtKeyType keyType, String digest, int saltLength, int fieldSize) {
        this.alg = alg;
        this.jcaName = jcaName;
        this.keyType = keyType;
        this.digest = digest;
        this.saltLength = saltLength;
        this.fieldSize = fieldSize;
    }

    public String alg() {
        return alg;
    }

    public String jcaName() {
        return jcaName;
    }

    public JwtKeyType keyType() {
        return keyType;
    }

    public String digest() {
        return digest;
    }

    public int saltLength() {
        return saltLength;
    }

    public int fieldSize() {
        return fieldSize;
    }

    public boolean isHmac() {
        return keyType == JwtKeyType.MAC;
    }

    public boolean isRsa() {
        return keyType == JwtKeyType.RSA;
    }

    public boolean isEc() {
        return keyType == JwtKeyType.EC;
    }

    public boolean isOkp() {
        return keyType == JwtKeyType.OKP;
    }

    public boolean isPss() {
        return digest != null;
    }

    public static JwtAlgorithm fromAlg(String alg) {
        for (JwtAlgorithm algorithm : values()) {
            if (algorithm.alg.equals(alg)) {
                return algorithm;
            }
        }
        throw new CryptoException("不支持的 JWT 算法：" + alg);
    }

    public static JwtAlgorithm forKey(Key key) {
        if (key instanceof SecretKey) {
            return HS256;
        }
        if (key instanceof RSAPublicKey || key instanceof java.security.interfaces.RSAPrivateKey || "RSA".equals(key.getAlgorithm())) {
            return RS256;
        }
        if (key instanceof EdECKey || "Ed25519".equals(key.getAlgorithm()) || "Ed448".equals(key.getAlgorithm()) || "EdDSA".equals(key.getAlgorithm())) {
            return EdDSA;
        }
        if (key instanceof ECKey ecKey) {
            int size = ecKey.getParams().getCurve().getField().getFieldSize();
            if (size <= 256) {
                return ES256;
            }
            return size <= 384 ? ES384 : ES512;
        }
        throw new CryptoException("不支持的 JWT 密钥类型：" + key.getAlgorithm());
    }
}
