package cn.slainc.changeaudit.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.utils.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ChangeAuditConfigReader {

    private static final Logger log = LoggerFactory.getLogger(ChangeAuditConfigReader.class);

    private final ChangeAuditProperties properties;

    private final ObjectProvider<DynamicConfigService> dynamicConfigService;

    public ChangeAuditConfigReader(ChangeAuditProperties properties,
                                   ObjectProvider<DynamicConfigService> dynamicConfigService) {
        this.properties = properties == null ? new ChangeAuditProperties() : properties;
        this.dynamicConfigService = dynamicConfigService;
    }

    public ChangeAuditProperties properties() {
        return properties;
    }

    public Optional<String> findString(String key) {
        DynamicConfigService service = dynamicConfigService == null ? null : dynamicConfigService.getIfAvailable();
        if (service == null) {
            return Optional.empty();
        }
        try {
            return service.findString(key);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public boolean enabled() {
        return bool(ChangeAuditProperties.KEY_ENABLED, properties.isEnabled());
    }

    public int maxValueLength() {
        return intValue(ChangeAuditProperties.KEY_MAX_VALUE_LENGTH, properties.getMaxValueLength());
    }

    public List<String> ignoreFields() {
        List<String> defaultValue = properties.getIgnoreFields();
        String raw = findString(ChangeAuditProperties.KEY_IGNORE_FIELDS).orElse(null);
        if (StringUtils.isBlank(raw)) {
            return defaultValue;
        }
        List<String> fields = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (StringUtils.isNotBlank(part)) {
                fields.add(part.trim());
            }
        }
        return fields.isEmpty() ? defaultValue : List.copyOf(fields);
    }

    public boolean recordNoChange() {
        return bool(ChangeAuditProperties.KEY_RECORD_NO_CHANGE, properties.isRecordNoChange());
    }

    public boolean async() {
        return bool(ChangeAuditProperties.KEY_ASYNC, properties.isAsync());
    }

    public int maxExportRows() {
        return intValue(ChangeAuditProperties.KEY_MAX_EXPORT_ROWS, properties.getMaxExportRows());
    }

    public boolean javersDiffEngine() {
        String engine = findString(ChangeAuditProperties.KEY_DIFF_ENGINE).orElse(properties.getDiffEngine());
        return "javers".equalsIgnoreCase(engine == null ? null : engine.trim());
    }

    private boolean bool(String key, boolean defaultValue) {
        String value = findString(key).orElse(null);
        return StringUtils.isBlank(value) ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    private int intValue(String key, int defaultValue) {
        String value = findString(key).orElse(null);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("动态配置 {} 不是合法整数，已退回默认值 {}", key, defaultValue);
            return defaultValue;
        }
    }
}
