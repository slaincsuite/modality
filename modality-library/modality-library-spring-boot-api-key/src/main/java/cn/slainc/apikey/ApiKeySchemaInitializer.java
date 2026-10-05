package cn.slainc.apikey;

import cn.slainc.core.jdbc.ClasspathSchemaInitializer;

import javax.sql.DataSource;

public class ApiKeySchemaInitializer extends ClasspathSchemaInitializer {

    public ApiKeySchemaInitializer(DataSource dataSource) {
        super(dataSource, "cn/slainc/apikey/api-key-schema.sql");
    }
}
