package cn.slainc.modules.audit.config;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.modules.audit.autoconfigure.AuditProperties;
import org.springframework.beans.factory.ObjectProvider;


// 热配置读取：本地 properties 为基线，DynamicConfigService 中的同名键实时覆盖（无该服务时只读本地值）
public class AuditConfigReader {

    public static final String KEY_API_SAMPLING_RATE = "modality.audit.api-log.sampling-rate";
    public static final String KEY_API_SLOW_THRESHOLD = "modality.audit.api-log.slow-threshold-millis";
    public static final String KEY_POLICY_RECORD_ALLOW = "modality.audit.policy-log.record-allow";
    public static final String KEY_CLEANUP_RETENTION_DAYS = "modality.audit.cleanup.retention-days";
    public static final String KEY_ALERT_RISKY_THRESHOLD = "modality.audit.alert.risky-operation-threshold";
    public static final String KEY_ALERT_LOGIN_FAILURE_THRESHOLD = "modality.audit.alert.login-failure-threshold";

    private final AuditProperties properties;

    private final DynamicConfigService dynamicConfig;

    public AuditConfigReader(AuditProperties properties, ObjectProvider<DynamicConfigService> dynamicConfig) {
        this.properties = properties;
        this.dynamicConfig = dynamicConfig.getIfAvailable();
    }

    public double apiSamplingRate() {
        if (dynamicConfig == null) {
            return properties.getApiLog().getSamplingRate();
        }
        return parseDouble(dynamicConfig.getString(KEY_API_SAMPLING_RATE,
                String.valueOf(properties.getApiLog().getSamplingRate())),
                properties.getApiLog().getSamplingRate());
    }

    public long apiSlowThresholdMillis() {
        if (dynamicConfig == null) {
            return properties.getApiLog().getSlowThresholdMillis();
        }
        return dynamicConfig.getLong(KEY_API_SLOW_THRESHOLD,
                properties.getApiLog().getSlowThresholdMillis());
    }

    public boolean policyRecordAllow() {
        if (dynamicConfig == null) {
            return properties.getPolicyLog().isRecordAllow();
        }
        return dynamicConfig.getBoolean(KEY_POLICY_RECORD_ALLOW,
                properties.getPolicyLog().isRecordAllow());
    }

    public int cleanupRetentionDays() {
        if (dynamicConfig == null) {
            return properties.getCleanup().getRetentionDays();
        }
        return dynamicConfig.getInt(KEY_CLEANUP_RETENTION_DAYS,
                properties.getCleanup().getRetentionDays());
    }

    public int alertRiskyThreshold() {
        if (dynamicConfig == null) {
            return properties.getAlert().getRiskyOperationThreshold();
        }
        return dynamicConfig.getInt(KEY_ALERT_RISKY_THRESHOLD,
                properties.getAlert().getRiskyOperationThreshold());
    }

    public int alertLoginFailureThreshold() {
        if (dynamicConfig == null) {
            return properties.getAlert().getLoginFailureThreshold();
        }
        return dynamicConfig.getInt(KEY_ALERT_LOGIN_FAILURE_THRESHOLD,
                properties.getAlert().getLoginFailureThreshold());
    }

    public AuditProperties properties() {
        return properties;
    }

    private double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
