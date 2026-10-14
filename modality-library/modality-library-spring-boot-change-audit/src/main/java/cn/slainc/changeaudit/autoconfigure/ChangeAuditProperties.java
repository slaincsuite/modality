package cn.slainc.changeaudit.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = ChangeAuditProperties.PREFIX)
public class ChangeAuditProperties {

    public static final String PREFIX = "modality.change-audit";

    public static final String KEY_ENABLED = PREFIX + ".enabled";

    public static final String KEY_MAX_VALUE_LENGTH = PREFIX + ".max-value-length";

    public static final String KEY_IGNORE_FIELDS = PREFIX + ".ignore-fields";

    public static final String KEY_RECORD_NO_CHANGE = PREFIX + ".record-no-change";

    public static final String KEY_ASYNC = PREFIX + ".async";

    public static final String KEY_MAX_EXPORT_ROWS = PREFIX + ".max-export-rows";

    public static final String KEY_DIFF_ENGINE = PREFIX + ".diff-engine";

    private boolean enabled = true;

    private int maxValueLength = 512;

    private List<String> ignoreFields = new ArrayList<>(
            List.of("createdBy", "createdAt", "updatedBy", "updatedAt", "version", "deleted"));

    private boolean recordNoChange;

    private boolean async;

    private int maxExportRows = 5000;

    private String diffEngine = "builtin";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxValueLength() {
        return maxValueLength;
    }

    public void setMaxValueLength(int maxValueLength) {
        this.maxValueLength = maxValueLength;
    }

    public List<String> getIgnoreFields() {
        return ignoreFields;
    }

    public void setIgnoreFields(List<String> ignoreFields) {
        this.ignoreFields = ignoreFields;
    }

    public boolean isRecordNoChange() {
        return recordNoChange;
    }

    public void setRecordNoChange(boolean recordNoChange) {
        this.recordNoChange = recordNoChange;
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public int getMaxExportRows() {
        return maxExportRows;
    }

    public void setMaxExportRows(int maxExportRows) {
        this.maxExportRows = maxExportRows;
    }

    public String getDiffEngine() {
        return diffEngine;
    }

    public void setDiffEngine(String diffEngine) {
        this.diffEngine = diffEngine;
    }
}
