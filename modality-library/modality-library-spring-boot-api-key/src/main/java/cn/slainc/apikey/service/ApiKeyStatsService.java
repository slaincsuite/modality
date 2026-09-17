package cn.slainc.apikey.service;

import cn.slainc.apikey.guard.ApiKeyStatsKeys;
import cn.slainc.apikey.guard.ApiKeyStatsRecorder;
import cn.slainc.core.constant.CacheKeys;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class ApiKeyStatsService {

    private final StringRedisTemplate redisTemplate;

    public ApiKeyStatsService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long total(String apiKey) {
        String value = read(CacheKeys.API_KEY + "stats:" + ApiKeyStatsKeys.fingerprint(apiKey) + ":"
                + ApiKeyStatsKeys.TOTAL_FIELD);
        return value == null ? 0 : Long.parseLong(value);
    }

    public Map<String, Long> daily(String apiKey, int days) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (days <= 0) {
            return result;
        }
        String fingerprint = ApiKeyStatsKeys.fingerprint(apiKey);
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            result.put(day.toString(), readAsLong(ApiKeyStatsRecorder.dailyKey(fingerprint, day)));
        }
        return result;
    }

    public Long lastUsed(String apiKey) {
        String value = readHash(apiKey);
        return value == null ? null : Long.parseLong(value);
    }

    private String readHash(String apiKey) {
        if (redisTemplate == null) {
            return null;
        }
        Object value = redisTemplate.opsForHash().get(CacheKeys.API_KEY + "stats:"
                + ApiKeyStatsKeys.fingerprint(apiKey) + ":meta", ApiKeyStatsKeys.LAST_USED_FIELD);
        return value == null ? null : String.valueOf(value);
    }

    private String read(String key) {
        if (redisTemplate == null) {
            return null;
        }
        return redisTemplate.opsForValue().get(key);
    }

    private long readAsLong(String key) {
        String value = read(key);
        return value == null ? 0 : Long.parseLong(value);
    }
}
