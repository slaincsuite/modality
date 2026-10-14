package cn.slainc.modules.infra.autoconfigure;

import cn.slainc.modules.infra.service.console.SystemConsoleService;
import cn.slainc.modules.infra.web.console.SystemConsoleController;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(prefix = "modality.infra", name = "enabled", matchIfMissing = true)
@Import(SystemConsoleController.class)
public class InfraSystemAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SystemConsoleService systemConsoleService(ObjectProvider<HealthEndpoint> healthEndpoint,
                                                     ObjectProvider<cn.slainc.monitor.alert.AlertService> alertService,
                                                     ObjectProvider<cn.slainc.monitor.alert.AlertNotifier> alertNotifiers,
                                                     ObjectProvider<MeterRegistry> meterRegistry) {
        return new SystemConsoleService(healthEndpoint, alertService, alertNotifiers, meterRegistry);
    }
}
