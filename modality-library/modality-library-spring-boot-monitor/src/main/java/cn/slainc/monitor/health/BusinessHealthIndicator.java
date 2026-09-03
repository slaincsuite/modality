package cn.slainc.monitor.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

import java.util.List;
import java.util.Map;

public class BusinessHealthIndicator implements HealthIndicator {

    private final List<BusinessHealthCheck> checks;

    public BusinessHealthIndicator(List<BusinessHealthCheck> checks) {
        this.checks = checks == null ? List.of() : List.copyOf(checks);
    }

    @Override
    public Health health() {
        if (checks.isEmpty()) {
            return Health.up().withDetail("checks", "未配置业务健康检查").build();
        }
        Health.Builder details = Health.up();
        boolean down = false;
        for (BusinessHealthCheck check : checks) {
            String name = check.name() == null || check.name().isBlank()
                    ? check.getClass().getSimpleName() : check.name();
            try {
                Health result = check.health();
                if (result == null) {
                    down = true;
                    details.withDetail(name, "检查未返回结果");
                    continue;
                }
                Status status = result.getStatus();
                Map<String, Object> checkDetails = result.getDetails();
                details.withDetail(name, checkDetails.isEmpty()
                        ? status.getCode() : status.getCode() + "/" + checkDetails);
                if (!Status.UP.equals(status)) {
                    down = true;
                }
            } catch (RuntimeException exception) {
                down = true;
                details.withDetail(name, "检查异常: " + exception.getMessage());
            }
        }
        Health.Builder builder = down ? Health.down() : Health.up();
        builder.withDetails(details.build().getDetails());
        return builder.build();
    }
}
