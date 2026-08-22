package cn.slainc.mybatis.dynamic;

import cn.slainc.mybatis.properties.MybatisProperties;
import com.baomidou.mybatisplus.extension.plugins.handler.TableNameHandler;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DefaultTableNameHandler implements TableNameHandler {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MybatisProperties properties;

    public DefaultTableNameHandler(MybatisProperties properties) {
        this.properties = properties;
    }

    @Override
    public String dynamicTableName(String sql, String tableName) {
        String explicit = DynamicTableNameHolder.get(tableName);
        if (explicit != null) {
            return explicit;
        }
        if (!properties.getDynamicTable().isEnabled()) {
            return tableName;
        }
        return tableName;
    }

    public static String monthly(String tableName) {
        return tableName + "_" + LocalDate.now().format(MONTH);
    }

    public static String daily(String tableName) {
        return tableName + "_" + LocalDate.now().format(DAY);
    }

    public static String byTenant(String tableName, Long tenantId) {
        return tableName + "_" + (tenantId == null ? "0" : tenantId);
    }
}
