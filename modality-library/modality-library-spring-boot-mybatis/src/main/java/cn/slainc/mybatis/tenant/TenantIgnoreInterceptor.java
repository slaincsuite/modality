package cn.slainc.mybatis.tenant;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;

@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
public class TenantIgnoreInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        boolean ignored = isIgnoredMethod(statement);
        if (!ignored) {
            return invocation.proceed();
        }
        TenantHelper.beginIgnore();
        try {
            return invocation.proceed();
        } finally {
            TenantHelper.endIgnore();
        }
    }

    private boolean isIgnoredMethod(MappedStatement statement) {
        String id = statement.getId();
        int index = id.lastIndexOf('.');
        if (index <= 0) {
            return false;
        }
        try {
            Class<?> mapperType = Class.forName(id.substring(0, index));
            String methodName = id.substring(index + 1);
            for (Method method : mapperType.getMethods()) {
                if (method.getName().equals(methodName)
                        && AnnotationUtils.findAnnotation(method, IgnoreTenant.class) != null) {
                    return true;
                }
            }
            return AnnotationUtils.findAnnotation(mapperType, IgnoreTenant.class) != null;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }
}
