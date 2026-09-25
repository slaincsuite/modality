package cn.slainc.modules.auth.autoconfigure;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;

public class AuthSchemaInitializer implements InitializingBean {

    private final DataSource dataSource;

    public AuthSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("cn/slainc/modules/auth/auth-schema.sql"));
        }
    }
}
