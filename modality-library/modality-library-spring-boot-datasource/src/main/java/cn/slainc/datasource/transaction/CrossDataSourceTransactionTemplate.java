package cn.slainc.datasource.transaction;

import cn.slainc.datasource.exception.DataSourceException;
import cn.slainc.datasource.manager.DynamicDataSourceManager;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class CrossDataSourceTransactionTemplate {

    private final DynamicDataSourceManager manager;

    private final PlatformTransactionManager transactionManager;

    public CrossDataSourceTransactionTemplate(DynamicDataSourceManager manager,
                                              PlatformTransactionManager transactionManager) {
        this.manager = manager;
        this.transactionManager = transactionManager;
    }

    public <T> T execute(Collection<String> dataSourceNames, Function<Map<String, JdbcTemplate>, T> action) {
        return execute(dataSourceNames, TransactionDefinition.PROPAGATION_REQUIRED, -1, action);
    }

    public <T> T execute(Collection<String> dataSourceNames, int propagation, int timeoutSeconds,
                         Function<Map<String, JdbcTemplate>, T> action) {
        Map<String, JdbcTemplate> templates = templates(dataSourceNames);
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(propagation);
        if (timeoutSeconds > 0) {
            template.setTimeout(timeoutSeconds);
        }
        return template.execute(status -> action.apply(templates));
    }

    public void executeWithoutResult(Collection<String> dataSourceNames,
                                     java.util.function.Consumer<Map<String, JdbcTemplate>> action) {
        execute(dataSourceNames, templates -> {
            action.accept(templates);
            return null;
        });
    }

    public Map<String, JdbcTemplate> templates(Collection<String> dataSourceNames) {
        Map<String, JdbcTemplate> templates = new LinkedHashMap<>();
        for (String name : dataSourceNames) {
            templates.put(name, new JdbcTemplate(realDataSource(name)));
        }
        return templates;
    }

    private DataSource realDataSource(String name) {
        DataSource dataSource = manager.dataSources().get(name);
        if (dataSource == null) {
            throw new DataSourceException("数据源不存在: " + name);
        }
        if (dataSource instanceof ItemDataSource item && item.getRealDataSource() != null) {
            return item.getRealDataSource();
        }
        return dataSource;
    }
}
