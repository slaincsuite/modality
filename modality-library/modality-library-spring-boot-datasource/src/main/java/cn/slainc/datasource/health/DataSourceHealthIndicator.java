package cn.slainc.datasource.health;

import cn.slainc.datasource.manager.DataSourceHealth;
import cn.slainc.datasource.manager.DynamicDataSourceManager;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.util.Map;

public class DataSourceHealthIndicator implements HealthIndicator {

    private final DynamicDataSourceManager manager;

    public DataSourceHealthIndicator(DynamicDataSourceManager manager) {
        this.manager = manager;
    }

    @Override
    public Health health() {
        Map<String, DataSourceHealth> states = manager.healthStates();
        Health.Builder builder = states.isEmpty() || states.values().stream().allMatch(DataSourceHealth::healthy)
                ? Health.up() : Health.down();
        builder.withDetail("primary", manager.primaryName());
        builder.withDetail("count", manager.dataSourceNames().size());
        for (String name : manager.dataSourceNames()) {
            DataSourceHealth health = states.get(name);
            builder.withDetail(name, health == null ? "尚未探测"
                    : health.status().getDescription() + "/" + health.message() + "/" + health.elapsedMillis() + "ms");
        }
        return builder.build();
    }
}
