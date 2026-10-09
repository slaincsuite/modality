package cn.slainc.datasource.definition;

import cn.slainc.datasource.enums.DataSourceStatus;
import cn.slainc.datasource.enums.DatabaseType;
import cn.slainc.datasource.enums.PoolType;

import java.io.Serializable;
import java.time.LocalDateTime;

public class DataSourceDefinition implements Serializable {

    private Long id;

    private String name;

    private String groupName;

    private DatabaseType databaseType;

    private PoolType poolType;

    private String xaDataSourceClassName;

    private String driverClassName;

    private String url;

    private String username;

    private String password;

    private DataSourceStatus status = DataSourceStatus.ENABLED;

    private Integer weight = 1;

    private Boolean primaryFlag = Boolean.FALSE;

    private Boolean healthCheckEnabled = Boolean.TRUE;

    private String failoverTarget;

    private String description;

    private Integer minimumIdle;

    private Integer maximumPoolSize;

    private Long connectionTimeout;

    private Long idleTimeout;

    private Long maxLifetime;

    private Long leakDetectionThreshold;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String createdBy;

    private String updatedBy;

    private Integer deleted = 0;

    private Integer version = 0;

    private Long tenantId;

    public boolean isEnabled() {
        return status == DataSourceStatus.ENABLED || status == DataSourceStatus.DEGRADED;
    }

    public DatabaseType resolveDatabaseType() {
        return databaseType != null ? databaseType : DatabaseType.resolve(url, driverClassName);
    }

    public String resolveGroupName() {
        return groupName == null || groupName.isBlank() ? name : groupName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    public PoolType getPoolType() {
        return poolType;
    }

    public void setPoolType(PoolType poolType) {
        this.poolType = poolType;
    }

    public String getXaDataSourceClassName() {
        return xaDataSourceClassName;
    }

    public void setXaDataSourceClassName(String xaDataSourceClassName) {
        this.xaDataSourceClassName = xaDataSourceClassName;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public DataSourceStatus getStatus() {
        return status;
    }

    public void setStatus(DataSourceStatus status) {
        this.status = status;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    public Boolean getPrimaryFlag() {
        return primaryFlag;
    }

    public void setPrimaryFlag(Boolean primaryFlag) {
        this.primaryFlag = primaryFlag;
    }

    public Boolean getHealthCheckEnabled() {
        return healthCheckEnabled;
    }

    public void setHealthCheckEnabled(Boolean healthCheckEnabled) {
        this.healthCheckEnabled = healthCheckEnabled;
    }

    public String getFailoverTarget() {
        return failoverTarget;
    }

    public void setFailoverTarget(String failoverTarget) {
        this.failoverTarget = failoverTarget;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getMinimumIdle() {
        return minimumIdle;
    }

    public void setMinimumIdle(Integer minimumIdle) {
        this.minimumIdle = minimumIdle;
    }

    public Integer getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setMaximumPoolSize(Integer maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    public Long getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Long getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(Long idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public Long getMaxLifetime() {
        return maxLifetime;
    }

    public void setMaxLifetime(Long maxLifetime) {
        this.maxLifetime = maxLifetime;
    }

    public Long getLeakDetectionThreshold() {
        return leakDetectionThreshold;
    }

    public void setLeakDetectionThreshold(Long leakDetectionThreshold) {
        this.leakDetectionThreshold = leakDetectionThreshold;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}
