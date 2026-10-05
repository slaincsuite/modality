package cn.slainc.core.jdbc;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;

public class ClasspathSchemaInitializer implements InitializingBean {

    protected final DataSource dataSource;

    private final String sqlLocation;

    public ClasspathSchemaInitializer(DataSource dataSource, String sqlLocation) {
        this.dataSource = dataSource;
        this.sqlLocation = sqlLocation;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        init();
    }

    public void init() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(sqlLocation));
        }
    }
}
