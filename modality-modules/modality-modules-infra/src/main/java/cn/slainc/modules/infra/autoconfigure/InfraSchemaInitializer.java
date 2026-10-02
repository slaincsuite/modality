package cn.slainc.modules.infra.autoconfigure;

import cn.slainc.redis.config.DynamicConfigSchema;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

public class InfraSchemaInitializer {

    private final DataSource dataSource;

    public InfraSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void init() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(DynamicConfigSchema.createTableSql(DynamicConfigSchema.DEFAULT_TABLE));
        }
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("cn/slainc/modules/infra/infra-schema.sql"));
        }
    }
}
