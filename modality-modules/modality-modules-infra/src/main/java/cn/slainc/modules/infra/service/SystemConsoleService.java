package cn.slainc.modules.infra.service;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.monitor.alert.AlertEvent;
import cn.slainc.monitor.alert.AlertLevel;
import cn.slainc.monitor.alert.AlertNotifier;
import cn.slainc.monitor.alert.AlertType;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;

public class SystemConsoleService {

    private static final Logger log = LoggerFactory.getLogger(SystemConsoleService.class);

    private static final int METRIC_NAME_LIMIT = 500;

    private final ObjectProvider<HealthEndpoint> healthEndpoint;

    private final ObjectProvider<cn.slainc.monitor.alert.AlertService> alertService;

    private final ObjectProvider<AlertNotifier> alertNotifiers;

    private final ObjectProvider<MeterRegistry> meterRegistry;

    public SystemConsoleService(ObjectProvider<HealthEndpoint> healthEndpoint,
                                ObjectProvider<cn.slainc.monitor.alert.AlertService> alertService,
                                ObjectProvider<AlertNotifier> alertNotifiers,
                                ObjectProvider<MeterRegistry> meterRegistry) {
        this.healthEndpoint = healthEndpoint;
        this.alertService = alertService;
        this.alertNotifiers = alertNotifiers;
        this.meterRegistry = meterRegistry;
    }

    public Map<String, Object> health() {
        HealthEndpoint endpoint = healthEndpoint.getIfAvailable();
        if (endpoint == null) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "健康检查端点未启用（缺少 actuator）");
        }
        HealthDescriptor health = endpoint.health();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("status", health.getStatus().getCode());
        view.put("message", "组件级健康明细请查看 /actuator/health");
        return view;
    }

    public Map<String, Object> alertRules() {
        cn.slainc.monitor.alert.AlertService service = alertService.getIfAvailable();
        if (service == null) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "告警服务未启用（缺少 monitor 告警配置）");
        }
        List<Map<String, Object>> rules = new ArrayList<>();
        service.rules().forEach(rule -> {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", rule.getId());
            view.put("name", rule.getName());
            view.put("metric", rule.getMetric());
            view.put("operator", String.valueOf(rule.getCompare()));
            view.put("threshold", rule.getThreshold());
            view.put("level", String.valueOf(rule.getLevel()));
            view.put("enabled", rule.isEnabled());
            view.put("firing", service.isFiring(rule.getId()));
            rules.add(view);
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rules", rules);
        result.put("count", rules.size());
        return result;
    }

    public int sendTestAlert(String level, String message) {
        List<AlertNotifier> notifiers = alertNotifiers.orderedStream().toList();
        if (notifiers.isEmpty()) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "没有可用的告警通知通道（monitor 告警未启用）");
        }
        AlertLevel safeLevel;
        try {
            safeLevel = AlertLevel.valueOf(
                    (level == null || level.isBlank() ? "INFO" : level).trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BizException(ResultCode.PARAM_TYPE_MISMATCH, "告警级别只允许 INFO、WARN、ERROR");
        }
        AlertEvent event = new AlertEvent("infra-test", "基础设施管理台测试告警", safeLevel, AlertType.TRIGGER,
                "infra.test", "次", 1, 0, System.currentTimeMillis(),
                message == null || message.isBlank() ? "这是一条测试告警" : message);
        int sent = 0;
        for (AlertNotifier notifier : notifiers) {
            if (!notifier.supports(event)) {
                continue;
            }
            try {
                notifier.send(event);
                sent++;
            } catch (RuntimeException exception) {
                log.warn("测试告警经通道 {} 发送失败：{}", notifier.name(), exception.getMessage());
            }
        }
        return sent;
    }

    public Map<String, Object> metrics() {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        Map<String, Object> view = new LinkedHashMap<>();
        if (registry == null) {
            view.put("enabled", false);
            view.put("message", "指标注册表未启用，明细请查看 /actuator/prometheus");
            return view;
        }
        List<String> names = registry.getMeters().stream()
                .map(Meter::getId)
                .map(id -> id.getName())
                .distinct()
                .sorted()
                .limit(METRIC_NAME_LIMIT)
                .toList();
        view.put("enabled", true);
        view.put("metricCount", names.size());
        view.put("names", names);
        view.put("message", "指标明细与采样值请查看 /actuator/prometheus");
        return view;
    }
}
