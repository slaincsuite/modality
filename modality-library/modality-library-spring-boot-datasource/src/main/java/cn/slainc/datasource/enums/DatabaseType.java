package cn.slainc.datasource.enums;

public enum DatabaseType {

    POSTGRE_SQL("PostgreSQL", "org.postgresql.Driver", "jdbc:postgresql:", false, "org.postgresql.xa.PGXADataSource"),
    MYSQL("MySQL", "com.mysql.cj.jdbc.Driver", "jdbc:mysql:", false, "com.mysql.cj.jdbc.MysqlXADataSource"),
    ORACLE("Oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:", false, "oracle.jdbc.xa.client.OracleXADataSource"),
    SQL_SERVER("SQL Server", "com.microsoft.sqlserver.jdbc.SQLServerDriver", "jdbc:sqlserver:", false,
            "com.microsoft.sqlserver.jdbc.SQLServerXADataSource"),
    DM("达梦", "dm.jdbc.driver.DmDriver", "jdbc:dm:", false, null),
    KINGBASE("人大金仓", "com.kingbase8.Driver", "jdbc:kingbase8:", false, null),
    SHENTONG("神通", "com.oscar.Driver", "jdbc:oscar:", false, null),
    OCEAN_BASE("OceanBase", "com.oceanbase.jdbc.Driver", "jdbc:oceanbase:", true, null),
    TIDB("TiDB", "com.mysql.cj.jdbc.Driver", "jdbc:mysql:", true, "com.mysql.cj.jdbc.MysqlXADataSource"),
    H2("H2", "org.h2.Driver", "jdbc:h2:", false, "org.h2.jdbcx.JdbcDataSource");

    private final String displayName;

    private final String driverClassName;

    private final String urlPrefix;

    private final boolean distributed;

    private final String xaDataSourceClassName;

    DatabaseType(String displayName, String driverClassName, String urlPrefix, boolean distributed,
                 String xaDataSourceClassName) {
        this.displayName = displayName;
        this.driverClassName = driverClassName;
        this.urlPrefix = urlPrefix;
        this.distributed = distributed;
        this.xaDataSourceClassName = xaDataSourceClassName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }

    public boolean isDistributed() {
        return distributed;
    }

    public String getXaDataSourceClassName() {
        return xaDataSourceClassName;
    }

    public boolean supportsXa() {
        return xaDataSourceClassName != null;
    }

    public static DatabaseType fromUrl(String url) {
        if (url == null) {
            return null;
        }
        String normalized = url.trim().toLowerCase();
        for (DatabaseType type : values()) {
            if (normalized.startsWith(type.urlPrefix)) {
                if (type == MYSQL && normalized.contains("oceanbase")) {
                    return OCEAN_BASE;
                }
                return type;
            }
        }
        if (normalized.contains("oceanbase")) {
            return OCEAN_BASE;
        }
        if (normalized.contains("kingbase")) {
            return KINGBASE;
        }
        return null;
    }

    public static DatabaseType fromDriver(String driverClassName) {
        if (driverClassName == null) {
            return null;
        }
        String normalized = driverClassName.trim();
        for (DatabaseType type : values()) {
            if (type.driverClassName.equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    public static DatabaseType resolve(String url, String driverClassName) {
        DatabaseType byXa = fromXaDriver(driverClassName);
        if (byXa != null) {
            return byXa;
        }
        DatabaseType byUrl = fromUrl(url);
        return byUrl != null ? byUrl : fromDriver(driverClassName);
    }

    public static DatabaseType fromXaDriver(String className) {
        if (className == null) {
            return null;
        }
        for (DatabaseType type : values()) {
            if (className.trim().equals(type.xaDataSourceClassName)) {
                return type;
            }
        }
        return null;
    }
}
