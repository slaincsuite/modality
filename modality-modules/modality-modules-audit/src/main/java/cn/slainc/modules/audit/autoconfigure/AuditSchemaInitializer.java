package cn.slainc.modules.audit.autoconfigure;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;


public class AuditSchemaInitializer {

    private final DataSource dataSource;

    public AuditSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void init() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("cn/slainc/modules/audit/audit-schema.sql"));
        }
    }
}
