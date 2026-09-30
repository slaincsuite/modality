package cn.slainc.modules.policy.autoconfigure;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;

public class PolicyCenterSchemaInitializer {

    private final DataSource dataSource;

    public PolicyCenterSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void init() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("cn/slainc/modules/policy/policy-center-schema.sql"));
        }
    }
}
