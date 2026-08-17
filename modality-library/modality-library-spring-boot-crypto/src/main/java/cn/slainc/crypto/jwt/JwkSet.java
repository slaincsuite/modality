package cn.slainc.crypto.jwt;

import cn.slainc.crypto.CryptoException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JwkSet {

    private final List<Jwk> keys;

    public JwkSet(List<Jwk> keys) {
        this.keys = keys == null ? List.of() : List.copyOf(keys);
    }

    public static JwkSet of(Jwk... keys) {
        return new JwkSet(List.of(keys));
    }

    public static JwkSet parse(String json) {
        Object parsed = Json.parse(json);
        if (!(parsed instanceof Map<?, ?> map) || !(map.get("keys") instanceof List<?> list)) {
            throw new CryptoException("JWKS 必须包含 keys 数组");
        }
        List<Jwk> keys = new ArrayList<>(list.size());
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> key)) {
                throw new CryptoException("JWKS 的 keys 元素必须是 JSON 对象");
            }
            Map<String, Object> values = new LinkedHashMap<>();
            key.forEach((name, value) -> values.put(String.valueOf(name), value));
            keys.add(Jwk.fromMap(values));
        }
        return new JwkSet(keys);
    }

    public List<Jwk> keys() {
        return keys;
    }

    public int size() {
        return keys.size();
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public Jwk first() {
        if (keys.isEmpty()) {
            throw new CryptoException("JWKS 中不包含任何密钥");
        }
        return keys.get(0);
    }

    public Jwk findByKid(String kid) {
        for (Jwk key : keys) {
            if (kid != null && kid.equals(key.kid())) {
                return key;
            }
        }
        return null;
    }

    public Jwk findByKidOrFirst(String kid) {
        Jwk key = findByKid(kid);
        return key != null ? key : first();
    }

    public Jwk requireByKid(String kid) {
        Jwk key = findByKid(kid);
        if (key == null) {
            throw new CryptoException("JWKS 中不存在标识为 " + kid + " 的密钥");
        }
        return key;
    }

    public JwkSet add(Jwk key) {
        List<Jwk> updated = new ArrayList<>(keys);
        updated.add(key);
        return new JwkSet(updated);
    }

    public JwkSet publicKeys() {
        List<Jwk> updated = new ArrayList<>(keys.size());
        for (Jwk key : keys) {
            updated.add(Jwk.publicOnly(key));
        }
        return new JwkSet(updated);
    }

    public Map<String, Object> toMap() {
        List<Map<String, Object>> entries = new ArrayList<>(keys.size());
        for (Jwk key : keys) {
            entries.add(key.toMap());
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("keys", entries);
        return values;
    }

    public String toJson() {
        return Json.write(toMap());
    }
}
