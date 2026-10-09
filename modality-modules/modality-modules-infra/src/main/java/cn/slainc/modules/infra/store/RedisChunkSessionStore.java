package cn.slainc.modules.infra.store;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

public class RedisChunkSessionStore implements ChunkSessionStore {

    public static final String KEY_PREFIX = "modality:infra:chunk:";

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    private final Duration ttl;

    public RedisChunkSessionStore(StringRedisTemplate redisTemplate, Duration ttl) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("stringRedisTemplate 不能为空");
        }
        this.redisTemplate = redisTemplate;
        this.ttl = ttl == null || ttl.isZero() || ttl.isNegative() ? DEFAULT_TTL : ttl;
    }

    @Override
    public void save(ChunkSession session) {
        redisTemplate.opsForValue().set(redisKey(session.uploadId()), encode(session), ttl);
    }

    @Override
    public ChunkSession find(String uploadId) {
        if (uploadId == null || uploadId.isBlank()) {
            return null;
        }
        String payload = redisTemplate.opsForValue().get(redisKey(uploadId));
        return payload == null ? null : decode(uploadId, payload);
    }

    @Override
    public void remove(String uploadId) {
        if (uploadId != null && !uploadId.isBlank()) {
            redisTemplate.delete(redisKey(uploadId));
        }
    }

    public static String redisKey(String uploadId) {
        return KEY_PREFIX + uploadId;
    }

    private static String encode(ChunkSession session) {
        return String.join("|",
                encodePart(session.key()),
                encodePart(session.filename()),
                encodePart(session.contentType()),
                encodePart(session.folder()),
                String.valueOf(session.partSize()),
                String.valueOf(session.totalSize()),
                encodePart(session.owner()));
    }

    private static ChunkSession decode(String uploadId, String payload) {
        String[] parts = payload.split("\\|", -1);
        if (parts.length < 7) {
            return null;
        }
        try {
            return new ChunkSession(uploadId, decodePart(parts[0]), decodePart(parts[1]), decodePart(parts[2]),
                    decodePart(parts[3]), Integer.parseInt(parts[4]), Long.parseLong(parts[5]), decodePart(parts[6]));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String encodePart(String text) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
    }

    private static String decodePart(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
    }
}
