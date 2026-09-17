package cn.slainc.apikey.guard;

import cn.slainc.core.constant.CacheKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class ApiKeyStatsRecorder {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyStatsRecorder.class);

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final Duration DAILY_RETENTION = Duration.ofHours(48);

    private final StringRedisTemplate redisTemplate;

    public ApiKeyStatsRecorder(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void record(String apiKey) {
        if (redisTemplate == null || apiKey == null || apiKey.isBlank()) {
            return;
        }
        String fingerprint = ApiKeyStatsKeys.fingerprint(apiKey);
        String dailyKey = dailyKey(fingerprint, LocalDate.now());
        try {
            redisTemplate.opsForValue().increment(CacheKeys.API_KEY + "stats:" + fingerprint + ":"
                    + ApiKeyStatsKeys.TOTAL_FIELD);
            redisTemplate.opsForValue().increment(dailyKey);
            redisTemplate.expire(dailyKey, DAILY_RETENTION);
            redisTemplate.opsForHash().put(CacheKeys.API_KEY + "stats:" + fingerprint + ":meta",
                    ApiKeyStatsKeys.LAST_USED_FIELD, String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
            log.warn("API Key 使用统计写入失败，key 指纹={}，原因={}", fingerprint, e.getMessage());
        }
    }

    public static String dailyKey(String fingerprint, LocalDate day) {
        return CacheKeys.API_KEY + "stats:" + fingerprint + ":daily:" + DAY_FORMAT.format(day);
    }
}
