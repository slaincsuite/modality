package cn.slainc.crypto.key;

import cn.slainc.crypto.CryptoException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import javax.crypto.SecretKey;

public final class MapCryptoKeyProvider implements CryptoKeyProvider {

    public static final String DEFAULT_KEY_ID = "default";

    public static final String DEFAULT_ALGORITHM = "SM4";

    private final Map<String, SecretKey> keys;

    private final String currentKeyId;

    public MapCryptoKeyProvider(Map<String, String> keys) {
        this(keys, defaultKeyId(keys), DEFAULT_ALGORITHM);
    }

    public MapCryptoKeyProvider(Map<String, String> keys, String currentKeyId, String algorithm) {
        if (keys == null || keys.isEmpty()) {
            throw new CryptoException("密钥映射不能为空");
        }
        Map<String, SecretKey> resolved = new LinkedHashMap<>();
        keys.forEach((keyId, value) -> resolved.put(keyId, SecretKeyUtils.decode(value, algorithm)));
        this.keys = Map.copyOf(resolved);
        this.currentKeyId = currentKeyId == null || currentKeyId.isEmpty() ? keys.keySet().iterator().next() : currentKeyId;
        if (!this.keys.containsKey(this.currentKeyId)) {
            throw new CryptoException("当前密钥标识不存在于密钥映射中：" + this.currentKeyId);
        }
    }

    public static MapCryptoKeyProvider sm4(Map<String, String> keys) {
        return new MapCryptoKeyProvider(keys, defaultKeyId(keys), "SM4");
    }

    private static String defaultKeyId(Map<String, String> keys) {
        return keys != null && keys.containsKey(DEFAULT_KEY_ID) ? DEFAULT_KEY_ID : null;
    }

    public static MapCryptoKeyProvider of(Map<String, String> keys, String currentKeyId, String algorithm) {
        return new MapCryptoKeyProvider(keys, currentKeyId, algorithm);
    }

    public static MapCryptoKeyProvider fromProperties(Properties properties, String prefix) {
        if (properties == null) {
            throw new CryptoException("配置不能为空");
        }
        String base = prefix == null || prefix.isEmpty() ? "" : prefix + ".";
        Map<String, String> keys = new LinkedHashMap<>();
        String current = null;
        for (String name : new TreeSet<>(properties.stringPropertyNames())) {
            if (!name.startsWith(base)) {
                continue;
            }
            String keyId = name.substring(base.length());
            if ("current".equals(keyId)) {
                current = properties.getProperty(name).trim();
                continue;
            }
            keys.put(keyId, properties.getProperty(name));
        }
        return new MapCryptoKeyProvider(keys, current, DEFAULT_ALGORITHM);
    }

    public static MapCryptoKeyProvider fromProperties(Properties properties) {
        return fromProperties(properties, "crypto.key");
    }

    @Override
    public SecretKey key(String keyId) {
        SecretKey key = keys.get(keyId);
        if (key == null) {
            throw new CryptoException("未知的密钥标识：" + keyId);
        }
        return key;
    }

    @Override
    public String currentKeyId() {
        return currentKeyId;
    }

    @Override
    public Set<String> keyIds() {
        return keys.keySet();
    }

    public Map<String, SecretKey> keys() {
        return keys;
    }
}
