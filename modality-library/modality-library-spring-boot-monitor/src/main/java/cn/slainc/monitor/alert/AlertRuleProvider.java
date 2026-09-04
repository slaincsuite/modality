package cn.slainc.monitor.alert;

import cn.slainc.monitor.autoconfigure.MonitorProperties;
import cn.slainc.monitor.config.MonitorDynamicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class AlertRuleProvider {

    private static final Logger log = LoggerFactory.getLogger(AlertRuleProvider.class);

    public static final String RULES_KEY = "modality.monitor.alert.rules";

    private final MonitorDynamicConfig config;

    private final MonitorProperties properties;

    public AlertRuleProvider(MonitorDynamicConfig config, MonitorProperties properties) {
        this.config = config;
        this.properties = properties;
    }

    public List<AlertRule> rules() {
        List<AlertRule> dynamic = config.getList(RULES_KEY, AlertRule.class);
        if (!dynamic.isEmpty()) {
            return validate(dynamic, "动态配置");
        }
        List<AlertRule> configured = properties.getAlert().getRules();
        if (configured != null && !configured.isEmpty()) {
            return validate(configured, "本地配置");
        }
        return properties.getAlert().isDefaultRules() ? defaults() : List.of();
    }

    private List<AlertRule> validate(List<AlertRule> rules, String source) {
        List<AlertRule> valid = new ArrayList<>();
        for (AlertRule rule : rules) {
            if (rule == null) {
                continue;
            }
            try {
                rule.validate();
                valid.add(rule);
            } catch (RuntimeException exception) {
                log.warn("忽略非法的告警规则（来源 {}）: {}", source, exception.getMessage());
            }
        }
        return valid;
    }

    public static List<AlertRule> defaults() {
        AlertRule heap = AlertRule.of("jvm-heap-usage-high", "JVM 堆内存使用率过高",
                MeterRegistryAlertMetricSource.JVM_HEAP_USAGE, AlertOperator.GREATER_THAN, 0.9d, 300L, AlertLevel.WARN);
        heap.setDescription("堆内存使用率连续 5 分钟高于 90%");
        AlertRule http5xx = AlertRule.of("http-5xx-rate-high", "HTTP 5xx 比例过高",
                MeterRegistryAlertMetricSource.HTTP_ERROR_RATE_5XX, AlertOperator.GREATER_THAN, 0.5d, 180L,
                AlertLevel.ERROR);
        http5xx.setDescription("5xx 响应占比连续 3 分钟高于 50%");
        AlertRule cpu = AlertRule.of("system-cpu-usage-high", "系统 CPU 使用率过高", "modality.system.cpu.usage",
                AlertOperator.GREATER_THAN, 0.9d, 300L, AlertLevel.WARN);
        cpu.setDescription("系统 CPU 使用率连续 5 分钟高于 90%");
        AlertRule disk = AlertRule.of("system-disk-usage-high", "磁盘分区使用率过高",
                MeterRegistryAlertMetricSource.SYSTEM_DISK_USAGE, AlertOperator.GREATER_THAN, 0.9d, 600L,
                AlertLevel.WARN);
        disk.setDescription("任一磁盘分区使用率连续 10 分钟高于 90%");
        AlertRule pool = AlertRule.of("db-pool-pending-high", "数据库连接池等待线程过多",
                MeterRegistryAlertMetricSource.DB_POOL_PENDING, AlertOperator.GREATER_THAN, 5d, 60L, AlertLevel.WARN);
        pool.setDescription("连接池等待连接的线程数连续 1 分钟超过 5");
        return List.of(heap, http5xx, cpu, disk, pool);
    }
}
