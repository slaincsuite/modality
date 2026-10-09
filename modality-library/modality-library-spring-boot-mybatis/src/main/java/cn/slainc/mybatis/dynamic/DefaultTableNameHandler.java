package cn.slainc.mybatis.dynamic;

import com.baomidou.mybatisplus.extension.plugins.handler.TableNameHandler;

public class DefaultTableNameHandler implements TableNameHandler {

    @Override
    public String dynamicTableName(String sql, String tableName) {
        String explicit = DynamicTableNameHolder.get(tableName);
        return explicit != null ? explicit : tableName;
    }
}
