package cn.slainc.tenant.isolation;

import java.util.LinkedHashMap;
import java.util.Map;

public class TenantInfo {

    public static final String METADATA_SCHEMA_NAME = "schemaName";

    public static final String METADATA_SEARCH_PATH = "search_path";

    public static final String METADATA_CURRENT_SCHEMA = "currentSchema";

    public static final String METADATA_DATASOURCE = "datasourceName";

    private Long tenantId;

    private String tenantCode;

    private String tenantName;

    private TenantIsolationStrategy isolationStrategy;

    private String schemaName;

    private String datasourceName;

    private final Map<String, String> metadata = new LinkedHashMap<>();

    public static TenantInfo of(Long tenantId, String tenantCode, TenantIsolationStrategy isolationStrategy) {
        TenantInfo info = new TenantInfo();
        info.setTenantId(tenantId);
        info.setTenantCode(tenantCode);
        info.setIsolationStrategy(isolationStrategy);
        return info;
    }

    public void putMetadata(String key, String value) {
        if (key != null && value != null) {
            metadata.put(key, value);
        }
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public TenantIsolationStrategy getIsolationStrategy() {
        return isolationStrategy;
    }

    public void setIsolationStrategy(TenantIsolationStrategy isolationStrategy) {
        this.isolationStrategy = isolationStrategy;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getDatasourceName() {
        return datasourceName;
    }

    public void setDatasourceName(String datasourceName) {
        this.datasourceName = datasourceName;
    }
}
