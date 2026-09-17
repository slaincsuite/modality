package cn.slainc.sign.composer;

import cn.slainc.crypto.asymmetric.RsaSignatureAlgorithm;
import cn.slainc.crypto.asymmetric.RsaUtils;
import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.codec.HexUtils;
import cn.slainc.crypto.hmac.HmacUtils;
import cn.slainc.crypto.key.PemUtils;
import cn.slainc.crypto.sm.Sm2Utils;
import cn.slainc.sign.SignAlgorithm;
import cn.slainc.sign.config.SignProperties;
import cn.slainc.sign.key.SignKeyDefinition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class SignComposer {

    private static final Set<String> V1_RESERVED = Set.of("sign");

    private static final Set<String> V2_RESERVED = Set.of("sign", "appId", "timestamp", "nonce");

    private final SignProperties properties;

    private final ConcurrentHashMap<String, PublicKey> publicKeyCache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, PrivateKey> privateKeyCache = new ConcurrentHashMap<>();

    public SignComposer(SignProperties properties) {
        this.properties = properties;
    }

    public String compose(int version, String appId, String timestamp, String nonce,
                          Map<String, List<String>> params, boolean includeEmpty) {
        String parameterPart = canonicalParameters(params, includeEmpty, version == 1 ? V1_RESERVED : V2_RESERVED);
        if (version == 1) {
            return parameterPart;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("appId=").append(text(appId))
                .append("&nonce=").append(text(nonce))
                .append("&timestamp=").append(text(timestamp));
        if (!parameterPart.isEmpty()) {
            builder.append('&').append(parameterPart);
        }
        return builder.toString();
    }

    public String sign(int version, SignAlgorithm algorithm, SignKeyDefinition key, String canonical) {
        if (algorithm == null || key == null) {
            throw new IllegalArgumentException("签名算法与密钥定义不能为空");
        }
        byte[] data = canonical.getBytes(StandardCharsets.UTF_8);
        if (version == 1) {
            if (!algorithm.isHmac()) {
                throw new IllegalArgumentException("v1 签名仅支持 HMAC 算法");
            }
            String input = canonical + "&key=" + key.secretText();
            return HexUtils.encode(HmacUtils.hmac(algorithm.hmacAlgorithm(), key.secretKey(), input));
        }
        byte[] raw = switch (algorithm) {
            case HMAC_SHA256, HMAC_SM3 -> HmacUtils.hmac(algorithm.hmacAlgorithm(), key.secretKey(), data);
            case RSA_SHA256 -> RsaUtils.sign(RsaSignatureAlgorithm.SHA256_WITH_RSA,
                    privateKey(key.getPrivateKeyPem()), data);
            case SM2 -> Sm2Utils.sign(privateKey(key.getPrivateKeyPem()), data);
        };
        return encode(raw);
    }

    public boolean matches(int version, SignAlgorithm algorithm, SignKeyDefinition key,
                           String canonical, String providedSign) {
        if (algorithm == null || key == null || providedSign == null || providedSign.isBlank()) {
            return false;
        }
        byte[] data = canonical.getBytes(StandardCharsets.UTF_8);
        if (algorithm.isHmac()) {
            String expected = sign(version, algorithm, key, canonical);
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    providedSign.getBytes(StandardCharsets.UTF_8));
        }
        byte[] signature;
        try {
            signature = decode(providedSign);
        } catch (RuntimeException exception) {
            return false;
        }
        try {
            return switch (algorithm) {
                case RSA_SHA256 -> RsaUtils.verify(RsaSignatureAlgorithm.SHA256_WITH_RSA,
                        publicKey(key.getPublicKeyPem()), data, signature);
                case SM2 -> Sm2Utils.verify(publicKey(key.getPublicKeyPem()), data, signature);
                default -> false;
            };
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String canonicalParameters(Map<String, List<String>> params, boolean includeEmpty, Set<String> reserved) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        TreeMap<String, List<String>> sorted = new TreeMap<>(params);
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : sorted.entrySet()) {
            String name = entry.getKey();
            if (name == null || reserved.contains(name)) {
                continue;
            }
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                if (includeEmpty) {
                    append(builder, name, "");
                }
                continue;
            }
            for (String value : values) {
                if (value == null || value.isEmpty()) {
                    if (includeEmpty) {
                        append(builder, name, "");
                    }
                    continue;
                }
                append(builder, name, value);
            }
        }
        return builder.toString();
    }

    private void append(StringBuilder builder, String name, String value) {
        if (builder.length() > 0) {
            builder.append('&');
        }
        builder.append(name).append('=').append(value);
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String encode(byte[] raw) {
        return properties.getOutputEncoding() == SignProperties.OutputEncoding.HEX
                ? HexUtils.encode(raw) : Base64Utils.encodeUrlNoPadding(raw);
    }

    private byte[] decode(String value) {
        return properties.getOutputEncoding() == SignProperties.OutputEncoding.HEX
                ? HexUtils.decode(value) : Base64Utils.decodeUrl(value);
    }

    private PrivateKey privateKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("缺少 privateKeyPem，无法生成签名；服务端验签只需要公钥");
        }
        return privateKeyCache.computeIfAbsent(pem, ignored -> PemUtils.readPrivateKey(pem));
    }

    private PublicKey publicKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("缺少 publicKeyPem，无法验签");
        }
        return publicKeyCache.computeIfAbsent(pem, ignored -> PemUtils.readPublicKey(pem));
    }
}
