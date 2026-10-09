package cn.slainc.crypto.jwt;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.CryptoProviders;
import cn.slainc.crypto.asymmetric.EcCurve;
import cn.slainc.crypto.codec.Base64Utils;
import java.io.IOException;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;

public final class Jwk {

    public static final String KEY_TYPE_RSA = "RSA";

    public static final String KEY_TYPE_EC = "EC";

    public static final String KEY_TYPE_OKP = "OKP";

    public static final String KEY_TYPE_OCT = "oct";

    private static final byte[] ED25519_PRIVATE_PREFIX = Base64Utils.decode("MC4CAQAwBQYDK2VwBCIEIA==");

    private static final byte[] ED25519_PUBLIC_PREFIX = Base64Utils.decode("MCowBQYDK2VwAyEA");

    private static final byte[] ED448_PRIVATE_PREFIX = Base64Utils.decode("MEcCAQAwBQYDK2VxBDsEOQ==");

    private static final byte[] ED448_PUBLIC_PREFIX = Base64Utils.decode("MEMwBQYDK2VxAzoA");

    private final Map<String, Object> values;

    private Jwk(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static Jwk fromMap(Map<String, Object> values) {
        if (values == null || values.get("kty") == null) {
            throw new CryptoException("JWK 必须包含 kty 字段");
        }
        return new Jwk(values);
    }

    public static Jwk fromJson(String json) {
        Object parsed = Json.parse(json);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new CryptoException("JWK 必须是 JSON 对象");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        map.forEach((key, value) -> values.put(String.valueOf(key), value));
        return fromMap(values);
    }

    public static Jwk fromPublicKey(PublicKey publicKey) {
        if (publicKey instanceof RSAPublicKey rsaPublicKey) {
            return new Jwk(mapOf(KEY_TYPE_RSA, "n", unsigned(rsaPublicKey.getModulus()), "e", unsigned(rsaPublicKey.getPublicExponent())));
        }
        if (publicKey instanceof ECPublicKey ecPublicKey) {
            int fieldSize = ecPublicKey.getParams().getCurve().getField().getFieldSize();
            return new Jwk(mapOf(KEY_TYPE_EC, "crv", EcCurve.fromFieldSize(fieldSize).jwkLabel(),
                    "x", fixed(ecPublicKey.getW().getAffineX(), (fieldSize + 7) / 8),
                    "y", fixed(ecPublicKey.getW().getAffineY(), (fieldSize + 7) / 8)));
        }
        if (isEdwards(publicKey)) {
            String curve = edwardsCurve(publicKey);
            return new Jwk(mapOf(KEY_TYPE_OKP, "crv", curve, "x", rawPublic(publicKey, edwardsLength(curve))));
        }
        throw new CryptoException("不支持的公钥类型：" + publicKey.getAlgorithm());
    }

    public static Jwk fromPrivateKey(PrivateKey privateKey) {
        if (privateKey instanceof RSAPrivateCrtKey rsaPrivateKey) {
            return new Jwk(mapOf(KEY_TYPE_RSA,
                    "n", unsigned(rsaPrivateKey.getModulus()),
                    "e", unsigned(rsaPrivateKey.getPublicExponent()),
                    "d", unsigned(rsaPrivateKey.getPrivateExponent()),
                    "p", unsigned(rsaPrivateKey.getPrimeP()),
                    "q", unsigned(rsaPrivateKey.getPrimeQ()),
                    "dp", unsigned(rsaPrivateKey.getPrimeExponentP()),
                    "dq", unsigned(rsaPrivateKey.getPrimeExponentQ()),
                    "qi", unsigned(rsaPrivateKey.getCrtCoefficient())));
        }
        if (privateKey instanceof RSAPrivateKey rsaPrivateKey) {
            return new Jwk(mapOf(KEY_TYPE_RSA, "n", unsigned(rsaPrivateKey.getModulus()), "d", unsigned(rsaPrivateKey.getPrivateExponent())));
        }
        if (privateKey instanceof ECPrivateKey ecPrivateKey) {
            int fieldSize = ecPrivateKey.getParams().getCurve().getField().getFieldSize();
            return new Jwk(mapOf(KEY_TYPE_EC, "crv", EcCurve.fromFieldSize(fieldSize).jwkLabel(), "d", fixed(ecPrivateKey.getS(), (fieldSize + 7) / 8)));
        }
        if (isEdwards(privateKey)) {
            String curve = edwardsCurve(privateKey);
            return new Jwk(mapOf(KEY_TYPE_OKP, "crv", curve, "d", rawPrivate(privateKey, edwardsLength(curve))));
        }
        throw new CryptoException("不支持的私钥类型：" + privateKey.getAlgorithm());
    }

    public static Jwk fromSecretKey(SecretKey secretKey, String algorithm) {
        Map<String, Object> values = mapOf(KEY_TYPE_OCT, "k", secretKey.getEncoded());
        if (algorithm != null) {
            values.put("alg", algorithm);
        }
        return new Jwk(values);
    }

    public static Jwk publicOnly(Jwk jwk) {
        Map<String, Object> values = new LinkedHashMap<>(jwk.values);
        values.keySet().removeAll(java.util.List.of("d", "p", "q", "dp", "dq", "qi", "k"));
        return new Jwk(values);
    }

    public String kty() {
        return string("kty");
    }

    public String kid() {
        return string("kid");
    }

    public String alg() {
        return string("alg");
    }

    public String use() {
        return string("use");
    }

    public String crv() {
        return string("crv");
    }

    public Jwk withKid(String kid) {
        return with("kid", kid);
    }

    public Jwk withAlg(String alg) {
        return with("alg", alg);
    }

    public Jwk withUse(String use) {
        return with("use", use);
    }

    public Jwk with(String name, Object value) {
        Map<String, Object> values = new LinkedHashMap<>(this.values);
        if (value == null) {
            values.remove(name);
        } else {
            values.put(name, value);
        }
        return new Jwk(values);
    }

    public boolean isPrivate() {
        return values.containsKey("d") || values.containsKey("k");
    }

    public Map<String, Object> toMap() {
        return new LinkedHashMap<>(values);
    }

    public String toJson() {
        return Json.write(values);
    }

    public PublicKey toPublicKey() {
        try {
            return switch (kty()) {
                case KEY_TYPE_RSA -> KeyFactory.getInstance("RSA", CryptoProviders.bouncyCastle())
                        .generatePublic(new RSAPublicKeySpec(integer("n"), integer("e")));
                case KEY_TYPE_EC -> KeyFactory.getInstance("EC", CryptoProviders.bouncyCastle())
                        .generatePublic(new ECPublicKeySpec(new ECPoint(integer("x"), integer("y")), parameters(crv())));
                case KEY_TYPE_OKP -> KeyFactory.getInstance(okpName(crv()), CryptoProviders.bouncyCastle())
                        .generatePublic(new X509EncodedKeySpec(concat(okpPublicPrefix(crv()), bytes("x"))));
                default -> throw new CryptoException("JWK 类型不支持导出公钥：" + kty());
            };
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("无法从 JWK 还原公钥", exception);
        }
    }

    public PrivateKey toPrivateKey() {
        try {
            return switch (kty()) {
                case KEY_TYPE_RSA -> KeyFactory.getInstance("RSA", CryptoProviders.bouncyCastle()).generatePrivate(values.containsKey("p")
                        ? new RSAPrivateCrtKeySpec(integer("n"), integer("e"), integer("d"), integer("p"), integer("q"), integer("dp"), integer("dq"), integer("qi"))
                        : new RSAPrivateKeySpec(integer("n"), integer("d")));
                case KEY_TYPE_EC -> KeyFactory.getInstance("EC", CryptoProviders.bouncyCastle())
                        .generatePrivate(new ECPrivateKeySpec(integer("d"), parameters(crv())));
                case KEY_TYPE_OKP -> KeyFactory.getInstance(okpName(crv()), CryptoProviders.bouncyCastle())
                        .generatePrivate(new PKCS8EncodedKeySpec(concat(okpPrivatePrefix(crv()), bytes("d"))));
                default -> throw new CryptoException("JWK 类型不支持导出私钥：" + kty());
            };
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("无法从 JWK 还原私钥", exception);
        }
    }

    public SecretKey toSecretKey(String algorithm) {
        byte[] key = bytes("k");
        if (key == null) {
            throw new CryptoException("JWK 不包含对称密钥");
        }
        return new SecretKeySpec(key, algorithm);
    }

    private String string(String name) {
        Object value = values.get(name);
        return value == null ? null : String.valueOf(value);
    }

    private byte[] bytes(String name) {
        String value = string(name);
        return value == null ? null : Base64Utils.decodeUrl(value);
    }

    private BigInteger integer(String name) {
        byte[] value = bytes(name);
        if (value == null) {
            throw new CryptoException("JWK 缺少字段：" + name);
        }
        return new BigInteger(1, value);
    }

    private static Map<String, Object> mapOf(String kty, Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("kty", kty);
        for (int i = 0; i < entries.length; i += 2) {
            values.put(String.valueOf(entries[i]), encode(entries[i + 1]));
        }
        return values;
    }

    private static Object encode(Object value) {
        if (value instanceof BigInteger integer) {
            return unsigned(integer);
        }
        if (value instanceof byte[] bytes) {
            return Base64Utils.encodeUrlNoPadding(bytes);
        }
        return value;
    }

    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64Utils.encodeUrlNoPadding(bytes);
    }

    private static String fixed(BigInteger value, int length) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > length && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        if (bytes.length > length) {
            throw new CryptoException("密钥参数长度超出曲线长度");
        }
        byte[] result = new byte[length];
        System.arraycopy(bytes, 0, result, length - bytes.length, bytes.length);
        return Base64Utils.encodeUrlNoPadding(result);
    }

    private static byte[] rawPublic(PublicKey key, int length) {
        byte[] bytes = SubjectPublicKeyInfo.getInstance(key.getEncoded()).getPublicKeyData().getBytes();
        if (bytes.length != length) {
            throw new CryptoException("EdDSA 公钥长度必须为 " + length + " 字节");
        }
        return bytes;
    }

    private static byte[] rawPrivate(PrivateKey key, int length) {
        try {
            byte[] content = ASN1OctetString.getInstance(PrivateKeyInfo.getInstance(key.getEncoded()).parsePrivateKey()).getOctets();
            byte[] seed = content.length == length ? content : ASN1OctetString.getInstance(content).getOctets();
            if (seed.length != length) {
                throw new CryptoException("EdDSA 私钥长度必须为 " + length + " 字节");
            }
            return seed;
        } catch (IOException exception) {
            throw new CryptoException("无法解析 EdDSA 私钥", exception);
        }
    }

    private static byte[] concat(byte[] prefix, byte[] value) {
        if (value == null) {
            throw new CryptoException("JWK 缺少必要的密钥字段");
        }
        byte[] result = new byte[prefix.length + value.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(value, 0, result, prefix.length, value.length);
        return result;
    }

    private static boolean isEdwards(java.security.Key key) {
        String algorithm = key.getAlgorithm();
        return "Ed25519".equals(algorithm) || "Ed448".equals(algorithm) || "EdDSA".equals(algorithm);
    }

    private static String edwardsCurve(java.security.Key key) {
        if (key instanceof EdECKey edKey) {
            return edKey.getParams().getName();
        }
        return "Ed448".equals(key.getAlgorithm()) ? "Ed448" : "Ed25519";
    }

    private static int edwardsLength(String curve) {
        return "Ed448".equals(curve) ? 57 : 32;
    }

    private static String okpName(String curve) {
        return "Ed448".equals(curve) ? "Ed448" : "Ed25519";
    }

    private static byte[] okpPrivatePrefix(String curve) {
        return "Ed448".equals(curve) ? ED448_PRIVATE_PREFIX : ED25519_PRIVATE_PREFIX;
    }

    private static byte[] okpPublicPrefix(String curve) {
        return "Ed448".equals(curve) ? ED448_PUBLIC_PREFIX : ED25519_PUBLIC_PREFIX;
    }

    private static ECParameterSpec parameters(String curve) {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC", CryptoProviders.bouncyCastle());
            parameters.init(new ECGenParameterSpec(EcCurve.fromJwkLabel(curve).curveName()));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("无法解析椭圆曲线参数：" + curve, exception);
        }
    }
}
