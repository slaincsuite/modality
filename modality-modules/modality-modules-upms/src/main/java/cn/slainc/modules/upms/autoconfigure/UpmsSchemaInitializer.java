package cn.slainc.modules.upms.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;

public class UpmsSchemaInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(UpmsSchemaInitializer.class);

    private static final String SCHEMA_PATH = "cn/slainc/modules/upms/upms-schema.sql";

    private final DataSource dataSource;

    public UpmsSchemaInitializer(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(SCHEMA_PATH));
        }
        log.info("UPMS 基础表已就绪");
    }
}
