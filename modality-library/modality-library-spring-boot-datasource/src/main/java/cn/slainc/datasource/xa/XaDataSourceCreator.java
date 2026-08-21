package cn.slainc.datasource.xa;

import cn.slainc.datasource.enums.DatabaseType;
import cn.slainc.datasource.enums.PoolType;
import cn.slainc.datasource.exception.DataSourceException;
import cn.slainc.datasource.properties.DataSourceProperties;
import com.atomikos.jdbc.AtomikosDataSourceBean;
import com.baomidou.dynamic.datasource.creator.DataSourceCreator;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import org.springframework.core.Ordered;

import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.lang.reflect.Method;

public class XaDataSourceCreator implements DataSourceCreator, Ordered {

    private final DataSourceProperties properties;

    public XaDataSourceCreator(DataSourceProperties properties) {
        this.properties = properties;
    }

    @Override
    public DataSource createDataSource(DataSourceProperty property) {
        DatabaseType databaseType = DatabaseType.resolve(property.getUrl(), property.getDriverClassName());
        String xaClassName = databaseType == null ? null : databaseType.getXaDataSourceClassName();
        if (xaClassName == null) {
            throw new DataSourceException("数据库类型 " + (databaseType == null ? "未知" : databaseType.getDisplayName())
                    + " 不支持 XA 数据源，无法启用跨数据源强一致事务");
        }
        XADataSource xaDataSource = newXaDataSource(xaClassName, property.getUrl(), property.getUsername(),
                property.getPassword());
        AtomikosDataSourceBean bean = new AtomikosDataSourceBean();
        bean.setXaDataSource(xaDataSource);
        bean.setUniqueResourceName(property.getPoolName());
        bean.setMinPoolSize(properties.getJta().getMinPoolSize());
        bean.setMaxPoolSize(properties.getJta().getMaxPoolSize());
        bean.setBorrowConnectionTimeout(properties.getJta().getBorrowConnectionTimeout());
        bean.setMaxIdleTime(properties.getJta().getMaxIdleTime());
        return bean;
    }

    @Override
    public boolean support(DataSourceProperty property) {
        Class<? extends DataSource> type = property.getType();
        return type != null && PoolType.ATOMIKOS.getClassName().equals(type.getName());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static XADataSource newXaDataSource(String className, String url, String username, String password) {
        Object instance = instantiate(className);
        invoke(instance, new String[]{"setUrl", "setURL"}, url);
        invoke(instance, new String[]{"setUser"}, username);
        invoke(instance, new String[]{"setPassword"}, password);
        return (XADataSource) instance;
    }

    private static Object instantiate(String className) {
        try {
            Class<?> type = Class.forName(className);
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new DataSourceException("无法创建 XA 数据源 " + className
                    + "，请确认对应 JDBC 驱动已在运行期类路径上", exception);
        }
    }

    private static void invoke(Object target, String[] methodNames, String value) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName, String.class);
                method.invoke(target, value);
                return;
            } catch (ReflectiveOperationException ignored) {
                // 换下一个候选方法名
            }
        }
        throw new DataSourceException("XA 数据源 " + target.getClass().getName() + " 缺少设置方法 " + String.join("/", methodNames));
    }
}
