package cn.slainc.tenant.isolation;

import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.sql.Driver;

final class H2TestDatabase {

    private static final String DRIVER_CLASS = "org.h2.Driver";

    private H2TestDatabase() {
    }

    static DataSource dataSource(String url) {
        return new SimpleDriverDataSource(driver(), url, "sa", "");
    }

    private static Driver driver() {
        try {
            return (Driver) Class.forName(DRIVER_CLASS).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("测试类路径缺少 H2 驱动，请在模块 build.gradle 增加 testRuntimeOnly libs.h2",
                    exception);
        }
    }
}
