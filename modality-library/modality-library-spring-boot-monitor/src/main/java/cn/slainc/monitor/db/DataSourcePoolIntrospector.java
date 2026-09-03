package cn.slainc.monitor.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class DataSourcePoolIntrospector {

    private static final Logger log = LoggerFactory.getLogger(DataSourcePoolIntrospector.class);

    private static final int MAX_DEPTH = 4;

    // dynamic-datasource 的 DynamicRoutingDataSource 通过 getDataSources() 暴露内部物理池，这里用反射解包以免引入模块依赖
    public Map<String, DataSource> flatten(Collection<DataSource> roots) {
        Map<String, DataSource> pools = new LinkedHashMap<>();
        if (roots == null) {
            return pools;
        }
        Set<DataSource> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (DataSource root : roots) {
            collect(root, pools, visited, 0);
        }
        return pools;
    }

    private void collect(DataSource dataSource, Map<String, DataSource> pools, Set<DataSource> visited, int depth) {
        if (dataSource == null || depth > MAX_DEPTH || !visited.add(dataSource)) {
            return;
        }
        Map<String, DataSource> children = children(dataSource);
        if (children.isEmpty()) {
            pools.putIfAbsent(name(dataSource, pools.size()), dataSource);
            return;
        }
        for (Map.Entry<String, DataSource> entry : children.entrySet()) {
            DataSource child = entry.getValue();
            if (child == null) {
                continue;
            }
            Map<String, DataSource> grandchildren = children(child);
            if (grandchildren.isEmpty()) {
                pools.putIfAbsent(entry.getKey(), child);
            } else {
                collect(child, pools, visited, depth + 1);
            }
        }
    }

    private Map<String, DataSource> children(DataSource dataSource) {
        Method method;
        try {
            method = dataSource.getClass().getMethod("getDataSources");
        } catch (NoSuchMethodException exception) {
            return Map.of();
        }
        try {
            Object value = method.invoke(dataSource);
            if (value instanceof Map<?, ?> map) {
                Map<String, DataSource> children = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getValue() instanceof DataSource child) {
                        children.put(String.valueOf(entry.getKey()), child);
                    }
                }
                return children;
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            log.debug("反射读取 {} 内部数据源失败: {}", dataSource.getClass().getName(), exception.getMessage());
        }
        return Map.of();
    }

    private static String name(DataSource dataSource, int index) {
        return dataSource.getClass().getSimpleName() + "-" + index;
    }
}
