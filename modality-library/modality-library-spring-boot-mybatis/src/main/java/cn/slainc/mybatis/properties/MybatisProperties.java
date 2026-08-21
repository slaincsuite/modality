package cn.slainc.mybatis.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "modality.mybatis")
public class MybatisProperties {

    private boolean enabled = true;

    private final Pagination pagination = new Pagination();

    private final Tenant tenant = new Tenant();

    private final SqlLog sqlLog = new SqlLog();

    private final Fill fill = new Fill();

    private final DataScope dataScope = new DataScope();

    private final DynamicTable dynamicTable = new DynamicTable();

    private final Encrypt encrypt = new Encrypt();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Pagination getPagination() {
        return pagination;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public SqlLog getSqlLog() {
        return sqlLog;
    }

    public Fill getFill() {
        return fill;
    }

    public DataScope getDataScope() {
        return dataScope;
    }

    public DynamicTable getDynamicTable() {
        return dynamicTable;
    }

    public Encrypt getEncrypt() {
        return encrypt;
    }

    public static class Pagination {

        private boolean enabled = true;

        private Long maxLimit = 500L;

        private boolean overflow = false;

        private boolean optimizeJoin = true;

        private boolean blockAttack = true;

        private boolean illegalSql = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Long getMaxLimit() {
            return maxLimit;
        }

        public void setMaxLimit(Long maxLimit) {
            this.maxLimit = maxLimit;
        }

        public boolean isOverflow() {
            return overflow;
        }

        public void setOverflow(boolean overflow) {
            this.overflow = overflow;
        }

        public boolean isOptimizeJoin() {
            return optimizeJoin;
        }

        public void setOptimizeJoin(boolean optimizeJoin) {
            this.optimizeJoin = optimizeJoin;
        }

        public boolean isBlockAttack() {
            return blockAttack;
        }

        public void setBlockAttack(boolean blockAttack) {
            this.blockAttack = blockAttack;
        }

        public boolean isIllegalSql() {
            return illegalSql;
        }

        public void setIllegalSql(boolean illegalSql) {
            this.illegalSql = illegalSql;
        }
    }

    public static class Tenant {

        private boolean enabled = true;

        private String column = "tenant_id";

        private boolean superAdminIgnores = true;

        private boolean schemaIsolation = false;

        private List<String> ignoreTables = new ArrayList<>(List.of(
                "t_dynamic_config", "t_data_source", "t_tenant", "t_tenant_quota", "t_tenant_package",
                "qrtz_triggers", "qrtz_cron_triggers", "qrtz_fired_triggers", "qrtz_job_details",
                "qrtz_locks", "qrtz_paused_trigger_grps", "qrtz_scheduler_state", "qrtz_simple_triggers",
                "qrtz_simprop_triggers", "qrtz_blob_triggers", "qrtz_calendars",
                "flyway_schema_history", "databasechangelog", "databasechangeloglock"));

        private List<String> ignoreTablePrefixes = new ArrayList<>(List.of("act_", "qrtz_", "gen_"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getColumn() {
            return column;
        }

        public void setColumn(String column) {
            this.column = column;
        }

        public boolean isSuperAdminIgnores() {
            return superAdminIgnores;
        }

        public void setSuperAdminIgnores(boolean superAdminIgnores) {
            this.superAdminIgnores = superAdminIgnores;
        }

        public boolean isSchemaIsolation() {
            return schemaIsolation;
        }

        public void setSchemaIsolation(boolean schemaIsolation) {
            this.schemaIsolation = schemaIsolation;
        }

        public List<String> getIgnoreTables() {
            return ignoreTables;
        }

        public void setIgnoreTables(List<String> ignoreTables) {
            this.ignoreTables = ignoreTables;
        }

        public List<String> getIgnoreTablePrefixes() {
            return ignoreTablePrefixes;
        }

        public void setIgnoreTablePrefixes(List<String> ignoreTablePrefixes) {
            this.ignoreTablePrefixes = ignoreTablePrefixes;
        }
    }

    public static class SqlLog {

        private boolean enabled = true;

        private long slowThreshold = 1000L;

        private boolean printParameters = true;

        private boolean printResult = false;

        private int maxResultRows = 20;

        private boolean async = true;

        private int queueCapacity = 4096;

        private double sampleRate = 0.0D;

        private boolean audit = false;

        private int slowTopSize = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getSlowThreshold() {
            return slowThreshold;
        }

        public void setSlowThreshold(long slowThreshold) {
            this.slowThreshold = slowThreshold;
        }

        public boolean isPrintParameters() {
            return printParameters;
        }

        public void setPrintParameters(boolean printParameters) {
            this.printParameters = printParameters;
        }

        public boolean isPrintResult() {
            return printResult;
        }

        public void setPrintResult(boolean printResult) {
            this.printResult = printResult;
        }

        public int getMaxResultRows() {
            return maxResultRows;
        }

        public void setMaxResultRows(int maxResultRows) {
            this.maxResultRows = maxResultRows;
        }

        public boolean isAsync() {
            return async;
        }

        public void setAsync(boolean async) {
            this.async = async;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public double getSampleRate() {
            return sampleRate;
        }

        public void setSampleRate(double sampleRate) {
            this.sampleRate = sampleRate;
        }

        public boolean isAudit() {
            return audit;
        }

        public void setAudit(boolean audit) {
            this.audit = audit;
        }

        public int getSlowTopSize() {
            return slowTopSize;
        }

        public void setSlowTopSize(int slowTopSize) {
            this.slowTopSize = slowTopSize;
        }
    }

    public static class Fill {

        private boolean enabled = true;

        private boolean fillTenant = true;

        private boolean fillAudit = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isFillTenant() {
            return fillTenant;
        }

        public void setFillTenant(boolean fillTenant) {
            this.fillTenant = fillTenant;
        }

        public boolean isFillAudit() {
            return fillAudit;
        }

        public void setFillAudit(boolean fillAudit) {
            this.fillAudit = fillAudit;
        }
    }

    public static class DataScope {

        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class DynamicTable {

        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Encrypt {

        private boolean enabled = false;

        private String secretKey;

        private String keyId = "modality";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }
    }
}
