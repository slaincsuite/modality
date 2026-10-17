package cn.slainc.modules.infra.store;

import cn.slainc.modules.infra.InfraKeys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public class ApiKeyRequestMetricsStore {

    public record Summary(long total, long failureCount, double averageElapsedMillis, Long lastUsedTime) {
    }

    private final JdbcTemplate jdbc;

    public ApiKeyRequestMetricsStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String apiKey, int httpStatus, long elapsedMillis) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        jdbc.update("INSERT INTO " + InfraKeys.API_KEY_METRIC_TABLE
                        + " (api_key_hash, http_status, elapsed_millis, create_time) VALUES (?, ?, ?, ?)",
                hash(apiKey), httpStatus, Math.max(0L, elapsedMillis), System.currentTimeMillis());
    }

    public Long lastUsed(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        List<Long> rows = jdbc.query("SELECT MAX(create_time) FROM " + InfraKeys.API_KEY_METRIC_TABLE
                        + " WHERE api_key_hash = ?",
                (rs, rowNum) -> rs.getObject(1) == null ? null : rs.getLong(1), hash(apiKey));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Summary summary(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return new Summary(0L, 0L, 0D, null);
        }
        return jdbc.queryForObject("SELECT COUNT(*),"
                        + " COALESCE(SUM(CASE WHEN http_status >= 400 THEN 1 ELSE 0 END), 0),"
                        + " COALESCE(AVG(elapsed_millis), 0), MAX(create_time)"
                        + " FROM " + InfraKeys.API_KEY_METRIC_TABLE + " WHERE api_key_hash = ?",
                (rs, rowNum) -> new Summary(rs.getLong(1), rs.getLong(2), rs.getDouble(3),
                        rs.getObject(4) == null ? null : rs.getLong(4)), hash(apiKey));
    }

    private static String hash(String apiKey) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(apiKey.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("API Key 指标指纹生成失败", exception);
        }
    }
}
