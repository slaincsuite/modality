package cn.slainc.apikey;

import org.springframework.beans.factory.InitializingBean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

public class ApiKeySchemaInitializer implements InitializingBean {

    private final DataSource dataSource;

    public ApiKeySchemaInitializer(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + ApiKeyRecord.TABLE_NAME + " ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "api_key VARCHAR(64) NOT NULL UNIQUE,"
                    + "title VARCHAR(128),"
                    + "intro VARCHAR(256),"
                    + "login_id VARCHAR(64) NOT NULL,"
                    + "scopes VARCHAR(512),"
                    + "extra_data TEXT,"
                    + "status VARCHAR(16) NOT NULL DEFAULT 'enabled',"
                    + "expires_time BIGINT NOT NULL DEFAULT -1,"
                    + "create_time BIGINT NOT NULL,"
                    + "update_time BIGINT NOT NULL)");
        }
    }
}
