package cn.slainc.jwt.key;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.asymmetric.EcCurve;
import cn.slainc.crypto.asymmetric.EdDsaAlgorithm;
import cn.slainc.crypto.asymmetric.EcdsaUtils;
import cn.slainc.crypto.asymmetric.EdDsaUtils;
import cn.slainc.crypto.asymmetric.RsaKeySize;
import cn.slainc.crypto.asymmetric.RsaUtils;
import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.jwt.JwtAlgorithm;
import cn.slainc.crypto.key.SecretKeyUtils;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECPoint;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X9ECParameters;

final class KeyMaterials {

    private static final byte[] ED25519_PUBLIC_PREFIX = Base64Utils.decode("MCowBQYDK2VwAyEA");

    private static final byte[] ED448_PUBLIC_PREFIX = Base64Utils.decode("MEMwBQYDK2VxAzoA");

    private KeyMaterials() {
    }

    static KeyPairMaterial generate(JwtAlgorithm algorithm) {
        return switch (algorithm) {
            case HS256 -> hmac(32, "HmacSHA256");
            case HS384 -> hmac(48, "HmacSHA384");
            case HS512 -> hmac(64, "HmacSHA512");
            case RS256, RS384, RS512, PS256, PS384, PS512 -> rsa(RsaKeySize.RSA_2048);
            case ES256 -> ec(EcCurve.P_256);
            case ES384 -> ec(EcCurve.P_384);
            case ES512 -> ec(EcCurve.P_521);
            case EdDSA -> eddsa(EdDsaAlgorithm.ED25519);
        };
    }

    private static KeyPairMaterial hmac(int length, String jcaName) {
        return new KeyPairMaterial(SecretKeyUtils.hmacKey(jcaName, length), null);
    }

    private static KeyPairMaterial rsa(RsaKeySize keySize) {
        var keyPair = RsaUtils.generateKeyPair(keySize);
        return new KeyPairMaterial(keyPair.getPrivate(), keyPair.getPublic());
    }

    private static KeyPairMaterial ec(EcCurve curve) {
        var keyPair = EcdsaUtils.generateKeyPair(curve);
        return new KeyPairMaterial(keyPair.getPrivate(), keyPair.getPublic());
    }

    private static KeyPairMaterial eddsa(EdDsaAlgorithm algorithm) {
        var keyPair = EdDsaUtils.generateKeyPair(algorithm);
        return new KeyPairMaterial(keyPair.getPrivate(), keyPair.getPublic());
    }

    static PublicKey derivePublicKey(PrivateKey privateKey) {
        try {
            if (privateKey instanceof RSAPrivateCrtKey crtKey) {
                return KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
            }
            if (privateKey instanceof ECPrivateKey ecPrivateKey) {
                return deriveEcPublic(ecPrivateKey);
            }
            if (privateKey instanceof EdECPrivateKey edPrivateKey) {
                return deriveEdPublic(edPrivateKey);
            }
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("无法从私钥推导公钥", exception);
        }
        throw new CryptoException("不支持从该类型私钥推导公钥：" + privateKey.getAlgorithm());
    }

    private static PublicKey deriveEcPublic(ECPrivateKey ecPrivateKey) throws GeneralSecurityException {
        ECParameterSpec spec = ecPrivateKey.getParams();
        X9ECParameters parameters = ECNamedCurveTable.getByName(curveName(spec));
        org.bouncycastle.math.ec.ECPoint q = parameters.getG().multiply(ecPrivateKey.getS()).normalize();
        ECPoint publicPoint = new ECPoint(q.getAffineXCoord().toBigInteger(), q.getAffineYCoord().toBigInteger());
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(publicPoint, spec));
    }

    private static PublicKey deriveEdPublic(EdECPrivateKey edPrivateKey) throws GeneralSecurityException {
        byte[] seed = edPrivateKey.getBytes()
                .orElseThrow(() -> new CryptoException("EdDSA 私钥缺少种子数据"));
        String algorithm = edPrivateKey.getAlgorithm();
        // X509 前缀之后跟的是公钥点编码而非种子，需要用 BC 从种子推导公钥
        if ("Ed448".equals(algorithm)) {
            byte[] publicKey = new byte[57];
            org.bouncycastle.math.ec.rfc8032.Ed448.generatePublicKey(seed, 0, publicKey, 0);
            return edPublicFromPrefix(ED448_PUBLIC_PREFIX, publicKey, algorithm);
        }
        byte[] publicKey = new byte[32];
        org.bouncycastle.math.ec.rfc8032.Ed25519.generatePublicKey(seed, 0, publicKey, 0);
        return edPublicFromPrefix(ED25519_PUBLIC_PREFIX, publicKey, algorithm);
    }

    private static PublicKey edPublicFromPrefix(byte[] prefix, byte[] publicKey, String algorithm)
            throws GeneralSecurityException {
        byte[] encoded = new byte[prefix.length + publicKey.length];
        System.arraycopy(prefix, 0, encoded, 0, prefix.length);
        System.arraycopy(publicKey, 0, encoded, prefix.length, publicKey.length);
        return KeyFactory.getInstance(algorithm).generatePublic(new X509EncodedKeySpec(encoded));
    }

    private static String curveName(ECParameterSpec spec) {
        if (spec instanceof org.bouncycastle.jce.spec.ECNamedCurveSpec namedSpec) {
            return namedSpec.getName();
        }
        int fieldSize = spec.getCurve().getField().getFieldSize();
        return switch (fieldSize) {
            case 256 -> "secp256r1";
            case 384 -> "secp384r1";
            case 521 -> "secp521r1";
            default -> throw new CryptoException("不支持的椭圆曲线位数：" + fieldSize);
        };
    }

    static int rsaKeyBits(Key key) {
        if (key instanceof RSAPublicKey rsaPublicKey) {
            return rsaPublicKey.getModulus().bitLength();
        }
        if (key instanceof RSAPrivateCrtKey crtKey) {
            return crtKey.getModulus().bitLength();
        }
        return 0;
    }

    record KeyPairMaterial(Key signingKey, PublicKey publicKey) {
    }
}
