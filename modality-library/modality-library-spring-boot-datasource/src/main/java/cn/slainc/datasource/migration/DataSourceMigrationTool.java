package cn.slainc.datasource.migration;

import cn.slainc.datasource.exception.DataSourceException;
import cn.slainc.datasource.manager.DynamicDataSourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DataSourceMigrationTool {

    private static final Logger log = LoggerFactory.getLogger(DataSourceMigrationTool.class);

    public static final int DEFAULT_BATCH_SIZE = 1000;

    private final DynamicDataSourceManager manager;

    public DataSourceMigrationTool(DynamicDataSourceManager manager) {
        this.manager = manager;
    }

    public List<String> listTables(String dataSourceName) {
        DataSource dataSource = require(dataSourceName);
        List<String> tables = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (resultSet.next()) {
                    tables.add(resultSet.getString("TABLE_NAME"));
                }
            }
        } catch (Exception exception) {
            throw new DataSourceException("读取数据源 [" + dataSourceName + "] 的表清单失败", exception);
        }
        return tables;
    }

    public int migrate(String tableName, String sourceName, String targetName) {
        return migrate(tableName, sourceName, targetName, DEFAULT_BATCH_SIZE);
    }

    public int migrate(String tableName, String sourceName, String targetName, int batchSize) {
        DataSource source = require(sourceName);
        DataSource target = require(targetName);
        String sql = "SELECT * FROM " + tableName;
        int total = 0;
        try (Connection sourceConnection = source.getConnection();
             Connection targetConnection = target.getConnection();
             Statement statement = sourceConnection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            String insertSql = buildInsertSql(tableName, metaData, columnCount);
            targetConnection.setAutoCommit(false);
            try (PreparedStatement insert = targetConnection.prepareStatement(insertSql)) {
                int pending = 0;
                while (resultSet.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        insert.setObject(i, resultSet.getObject(i));
                    }
                    insert.addBatch();
                    pending++;
                    if (pending >= batchSize) {
                        insert.executeBatch();
                        targetConnection.commit();
                        total += pending;
                        pending = 0;
                    }
                }
                if (pending > 0) {
                    insert.executeBatch();
                    targetConnection.commit();
                    total += pending;
                }
            }
        } catch (Exception exception) {
            throw new DataSourceException("迁移表 " + tableName + " 从 [" + sourceName + "] 到 [" + targetName + "] 失败", exception);
        }
        log.info("表 {} 已从 [{}] 迁移到 [{}]，共 {} 行", tableName, sourceName, targetName, total);
        return total;
    }

    private DataSource require(String name) {
        DataSource dataSource = manager.dataSources().get(name);
        if (dataSource == null) {
            throw new DataSourceException("数据源不存在: " + name);
        }
        return dataSource;
    }

    private static String buildInsertSql(String tableName, ResultSetMetaData metaData, int columnCount) throws Exception {
        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) {
                columns.append(", ");
                placeholders.append(", ");
            }
            columns.append(metaData.getColumnName(i));
            placeholders.append('?');
        }
        return "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ")";
    }
}
