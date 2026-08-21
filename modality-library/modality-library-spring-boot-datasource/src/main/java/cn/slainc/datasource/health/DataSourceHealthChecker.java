package cn.slainc.datasource.health;

import cn.slainc.datasource.enums.DataSourceStatus;
import cn.slainc.datasource.manager.DataSourceHealth;
import cn.slainc.datasource.properties.DataSourceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;

public class DataSourceHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(DataSourceHealthChecker.class);

    private final DataSourceProperties properties;

    public DataSourceHealthChecker(DataSourceProperties properties) {
        this.properties = properties;
    }

    public DataSourceHealth check(String name, DataSource dataSource) {
        long start = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(properties.getHealth().getTimeoutSeconds());
            long elapsed = elapsedMillis(start);
            warnSlowConnection(name, elapsed);
            if (!valid) {
                return new DataSourceHealth(name, DataSourceStatus.DEGRADED, false, elapsed, "连接校验未通过", LocalDateTime.now());
            }
            return new DataSourceHealth(name, DataSourceStatus.ENABLED, true, elapsed, "正常", LocalDateTime.now());
        } catch (Exception exception) {
            long elapsed = elapsedMillis(start);
            log.warn("数据源 [{}] 健康检查失败，耗时 {} 毫秒: {}", name, elapsed, exception.getMessage());
            return new DataSourceHealth(name, DataSourceStatus.DEGRADED, false, elapsed,
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                    LocalDateTime.now());
        }
    }

    private void warnSlowConnection(String name, long elapsed) {
        long threshold = properties.getHealth().getSlowConnectionThreshold();
        if (threshold > 0 && elapsed > threshold) {
            log.warn("数据源 [{}] 获取连接耗时 {} 毫秒，超过阈值 {} 毫秒", name, elapsed, threshold);
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
