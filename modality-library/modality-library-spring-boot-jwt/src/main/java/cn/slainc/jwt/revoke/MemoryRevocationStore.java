package cn.slainc.jwt.revoke;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryRevocationStore implements RevocationStore {

    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    @Override
    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("吊销记录必须携带 jti");
        }
        revoked.put(jti, expiresAt == null ? Instant.now().plusSeconds(86400) : expiresAt);
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Instant>> iterator = revoked.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Instant> entry = iterator.next();
            if (!now.isBefore(entry.getValue())) {
                iterator.remove();
            }
        }
        return revoked.containsKey(jti);
    }

    public int size() {
        return revoked.size();
    }
}
