package cn.slainc.crypto.jwt;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.CryptoProviders;
import cn.slainc.crypto.asymmetric.JcaSignatures;
import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.util.ConstantTimeUtils;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECKey;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class JwtCodec {

    private JwtCodec() {
    }

    public static String encode(JwtAlgorithm algorithm, Key key, Map<String, Object> claims) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("typ", "JWT");
        return encode(algorithm, key, header, claims);
    }

    public static String encode(JwtAlgorithm algorithm, Key key, Map<String, Object> header, Map<String, Object> claims) {
        if (claims == null) {
            throw new CryptoException("JWT 载荷不能为空");
        }
        Map<String, Object> headers = new LinkedHashMap<>(header == null ? Map.of() : header);
        headers.put("alg", algorithm.alg());
        String encodedHeader = base64Url(Json.write(headers));
        String encodedClaims = base64Url(Json.write(claims));
        String signingInput = encodedHeader + "." + encodedClaims;
        byte[] signature = sign(algorithm, key, signingInput.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + base64Url(signature);
    }

    public static Jwt decode(String token, Key key) {
        return decode(token, key, null);
    }

    public static Jwt decode(String token, Key key, JwtAlgorithm expected) {
        Jwt jwt = decodeUnverified(token);
        verifySignature(jwt, key, expected);
        return jwt;
    }

    public static Jwt decodeUnverified(String token) {
        if (token == null || token.isBlank()) {
            throw new CryptoException("JWT 不能为空");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new CryptoException("JWT 格式非法，必须由三段组成");
        }
        Map<String, Object> header = asObject(Json.parse(Base64Utils.decodeUrl(parts[0]).length == 0 ? "{}" : new String(Base64Utils.decodeUrl(parts[0]), StandardCharsets.UTF_8)), "JWT 头部");
        Map<String, Object> claims = asObject(Json.parse(Base64Utils.decodeUrl(parts[1]).length == 0 ? "{}" : new String(Base64Utils.decodeUrl(parts[1]), StandardCharsets.UTF_8)), "JWT 载荷");
        byte[] signature = parts[2].isEmpty() ? new byte[0] : Base64Utils.decodeUrl(parts[2]);
        return new Jwt(token, header, claims, signature, parts[0] + "." + parts[1]);
    }

    public static boolean verify(String token, Key key) {
        return verify(token, key, null);
    }

    public static boolean verify(String token, Key key, JwtAlgorithm expected) {
        try {
            verifySignature(decodeUnverified(token), key, expected);
            return true;
        } catch (CryptoException exception) {
            return false;
        }
    }

    public static String algorithm(String token) {
        return decodeUnverified(token).algorithm();
    }

    private static void verifySignature(Jwt jwt, Key key, JwtAlgorithm expected) {
        JwtAlgorithm algorithm = JwtAlgorithm.fromAlg(jwt.algorithm());
        if (expected != null && expected != algorithm) {
            throw new CryptoException("JWT 算法不匹配，期望 " + expected.alg() + "，实际 " + algorithm.alg());
        }
        checkKeyType(algorithm, key);
        byte[] data = jwt.signingInput().getBytes(StandardCharsets.UTF_8);
        boolean valid = algorithm.isHmac()
                ? ConstantTimeUtils.equals(mac(algorithm, key, data), jwt.signature())
                : JcaSignatures.verify(algorithm.jcaName(), algorithm.digest(), algorithm.saltLength(), (PublicKey) key, data, jwt.signature());
        if (!valid) {
            throw new CryptoException("JWT 签名校验失败");
        }
    }

    private static byte[] sign(JwtAlgorithm algorithm, Key key, byte[] data) {
        checkKeyType(algorithm, key);
        if (algorithm.isHmac()) {
            return mac(algorithm, key, data);
        }
        return JcaSignatures.sign(algorithm.jcaName(), algorithm.digest(), algorithm.saltLength(), (PrivateKey) key, data);
    }

    private static byte[] mac(JwtAlgorithm algorithm, Key key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(algorithm.jcaName(), CryptoProviders.bouncyCastle());
            mac.init(new SecretKeySpec(key.getEncoded(), algorithm.jcaName()));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new CryptoException("JWT HMAC 计算失败", exception);
        }
    }

    private static void checkKeyType(JwtAlgorithm algorithm, Key key) {
        if (key == null) {
            throw new CryptoException("JWT 密钥不能为空");
        }
        if (algorithm.isHmac() && !(key instanceof SecretKey)) {
            throw new CryptoException("JWT 算法 " + algorithm.alg() + " 需要对称密钥");
        }
        if (!algorithm.isHmac() && key instanceof SecretKey) {
            throw new CryptoException("JWT 算法 " + algorithm.alg() + " 需要非对称密钥");
        }
        if (algorithm.isRsa() && !"RSA".equals(key.getAlgorithm()) && !key.getAlgorithm().startsWith("RSA")) {
            throw new CryptoException("JWT 算法 " + algorithm.alg() + " 需要 RSA 密钥");
        }
        if (algorithm.isEc()) {
            if (!(key instanceof ECKey ecKey)) {
                throw new CryptoException("JWT 算法 " + algorithm.alg() + " 需要 EC 密钥");
            }
            int size = ecKey.getParams().getCurve().getField().getFieldSize();
            if (size != algorithm.fieldSize()) {
                throw new CryptoException("JWT 算法 " + algorithm.alg() + " 需要 " + algorithm.fieldSize() + " 位曲线，实际为 " + size + " 位");
            }
        }
        if (algorithm.isOkp() && !"Ed25519".equals(key.getAlgorithm()) && !"Ed448".equals(key.getAlgorithm()) && !"EdDSA".equals(key.getAlgorithm())) {
            throw new CryptoException("JWT 算法 " + algorithm.alg() + " 需要 EdDSA 密钥");
        }
    }

    private static String base64Url(byte[] data) {
        return Base64Utils.encodeUrlNoPadding(data);
    }

    private static String base64Url(String text) {
        return base64Url(text.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> asObject(Object value, String name) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new CryptoException(name + "必须是 JSON 对象");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, element) -> result.put(String.valueOf(key), element));
        return result;
    }
}
