package cn.slainc.datasource.failover;

import cn.slainc.datasource.definition.DataSourceDefinition;
import cn.slainc.datasource.manager.DataSourceHealth;
import cn.slainc.datasource.manager.DynamicDataSourceManager;
import cn.slainc.datasource.properties.DataSourceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

public class DataSourceFailoverManager {

    private static final Logger log = LoggerFactory.getLogger(DataSourceFailoverManager.class);

    private final DynamicDataSourceManager manager;

    private final DataSourceProperties properties;

    private final ApplicationEventPublisher eventPublisher;

    private volatile String failoverFrom;

    private volatile String failoverTo;

    public DataSourceFailoverManager(DynamicDataSourceManager manager,
                                     DataSourceProperties properties,
                                     ApplicationEventPublisher eventPublisher) {
        this.manager = manager;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    public synchronized void observe(DataSourceHealth health) {
        if (!properties.getHealth().isFailoverEnabled()) {
            return;
        }
        if (health.healthy()) {
            restore(health.name());
        } else {
            failover(health.name());
        }
    }

    private void failover(String failedName) {
        if (!failedName.equals(manager.primaryName())) {
            return;
        }
        Optional<String> candidate = selectCandidate(failedName);
        if (candidate.isEmpty()) {
            log.warn("数据源 [{}] 不可用，但没有可用的备选数据源", failedName);
            return;
        }
        if (failoverFrom == null) {
            failoverFrom = failedName;
        }
        failoverTo = candidate.get();
        manager.setPrimary(failoverTo);
        log.warn("数据源 [{}] 不可用，已自动切换到 [{}]", failedName, failoverTo);
        eventPublisher.publishEvent(new DataSourceFailoverEvent(failedName, failoverTo, true));
    }

    private void restore(String healthyName) {
        if (failoverFrom == null || !healthyName.equals(failoverFrom)) {
            return;
        }
        String previous = failoverTo;
        manager.setPrimary(failoverFrom);
        log.info("数据源 [{}] 已恢复，主库从 [{}] 切回", failoverFrom, previous);
        eventPublisher.publishEvent(new DataSourceFailoverEvent(previous, failoverFrom, false));
        failoverFrom = null;
        failoverTo = null;
    }

    private Optional<String> selectCandidate(String failedName) {
        Optional<DataSourceDefinition> definition = manager.findDefinition(failedName);
        if (definition.isPresent()) {
            String target = definition.get().getFailoverTarget();
            if (target != null && !target.isBlank() && manager.exists(target)) {
                return Optional.of(target);
            }
        }
        String group = loadBalanceGroup(failedName);
        return manager.dataSourceNames().stream()
                .filter(name -> !name.equals(failedName))
                .filter(name -> loadBalanceGroup(name).equals(group))
                .findFirst();
    }

    private static String loadBalanceGroup(String name) {
        int index = name.indexOf('_');
        return index > 0 ? name.substring(0, index) : name;
    }
}
