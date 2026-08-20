package cn.slainc.datasource.enums;

public enum PoolType {

    HIKARI("HikariCP", "com.zaxxer.hikari.HikariDataSource"),
    DRUID("Druid", "com.alibaba.druid.pool.DruidDataSource"),
    ATOMIKOS("Atomikos XA", "com.atomikos.jdbc.AtomikosDataSourceBean");

    private final String description;

    private final String className;

    PoolType(String description, String className) {
        this.description = description;
        this.className = className;
    }

    public String getDescription() {
        return description;
    }

    public String getClassName() {
        return className;
    }
}
