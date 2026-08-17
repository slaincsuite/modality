package cn.slainc.crypto.key;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.random.SecureRandomUtils;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class KeyRing {

    public static final String DEFAULT_KEY_ID = "default";

    private final String algorithm;

    private final int keyLength;

    private final Map<String, SecretKey> keys;

    private final String currentKeyId;

    private KeyRing(String algorithm, int keyLength, Map<String, SecretKey> keys, String currentKeyId) {
        if (keys.isEmpty()) {
            throw new CryptoException("密钥环必须至少包含一个密钥");
        }
        if (!keys.containsKey(currentKeyId)) {
            throw new CryptoException("当前密钥标识不存在于密钥环中：" + currentKeyId);
        }
        this.algorithm = algorithm;
        this.keyLength = keyLength;
        this.keys = Collections.unmodifiableMap(new LinkedHashMap<>(keys));
        this.currentKeyId = currentKeyId;
    }

    public static KeyRing create(String algorithm, int keyLength) {
        return create(algorithm, keyLength, DEFAULT_KEY_ID);
    }

    public static KeyRing create(String algorithm, int keyLength, String keyId) {
        Map<String, SecretKey> keys = new LinkedHashMap<>();
        keys.put(keyId, generate(algorithm, keyLength));
        return new KeyRing(algorithm, keyLength, keys, keyId);
    }

    public static KeyRing of(SecretKey key) {
        return of(DEFAULT_KEY_ID, key);
    }

    public static KeyRing of(String keyId, SecretKey key) {
        Map<String, SecretKey> keys = new LinkedHashMap<>();
        keys.put(keyId, key);
        return new KeyRing(key.getAlgorithm(), key.getEncoded().length, keys, keyId);
    }

    public KeyRing add(String keyId, SecretKey key) {
        Map<String, SecretKey> updated = new LinkedHashMap<>(keys);
        updated.put(keyId, key);
        return new KeyRing(algorithm, keyLength, updated, currentKeyId);
    }

    public KeyRing add(SecretKey key) {
        return add(SecureRandomUtils.nextHex(8), key);
    }

    public KeyRing rotate() {
        return rotate(SecureRandomUtils.nextHex(8));
    }

    public KeyRing rotate(String keyId) {
        Map<String, SecretKey> updated = new LinkedHashMap<>(keys);
        updated.put(keyId, generate(algorithm, keyLength));
        return new KeyRing(algorithm, keyLength, updated, keyId);
    }

    public KeyRing withCurrent(String keyId) {
        return new KeyRing(algorithm, keyLength, keys, keyId);
    }

    public KeyRing remove(String keyId) {
        Map<String, SecretKey> updated = new LinkedHashMap<>(keys);
        if (updated.remove(keyId) == null) {
            throw new CryptoException("未知的密钥标识：" + keyId);
        }
        if (updated.isEmpty()) {
            throw new CryptoException("密钥环必须至少包含一个密钥");
        }
        return new KeyRing(algorithm, keyLength, updated, currentKeyId.equals(keyId) ? updated.keySet().iterator().next() : currentKeyId);
    }

    public SecretKey key(String keyId) {
        SecretKey key = keys.get(keyId);
        if (key == null) {
            throw new CryptoException("未知的密钥标识：" + keyId);
        }
        return key;
    }

    public SecretKey currentKey() {
        return key(currentKeyId);
    }

    public byte[] currentKeyBytes() {
        return currentKey().getEncoded();
    }

    public String currentKeyId() {
        return currentKeyId;
    }

    public String algorithm() {
        return algorithm;
    }

    public int keyLength() {
        return keyLength;
    }

    public Set<String> keyIds() {
        return keys.keySet();
    }

    public Map<String, SecretKey> keys() {
        return keys;
    }

    private static SecretKey generate(String algorithm, int keyLength) {
        return new SecretKeySpec(SecureRandomUtils.nextBytes(keyLength), algorithm);
    }
}
