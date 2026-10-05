package cn.slainc.modules.infra.autoconfigure;

import cn.slainc.core.jdbc.ClasspathSchemaInitializer;
import cn.slainc.redis.config.DynamicConfigSchema;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

public class InfraSchemaInitializer extends ClasspathSchemaInitializer {

    public InfraSchemaInitializer(DataSource dataSource) {
        super(dataSource, "cn/slainc/modules/infra/infra-schema.sql");
    }

    @Override
    public void init() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(DynamicConfigSchema.createTableSql(DynamicConfigSchema.DEFAULT_TABLE));
        }
        super.init();
    }
}
