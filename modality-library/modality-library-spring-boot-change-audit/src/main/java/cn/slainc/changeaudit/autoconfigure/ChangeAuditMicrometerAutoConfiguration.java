package cn.slainc.changeaudit.autoconfigure;

import cn.slainc.changeaudit.dao.JdbcChangeRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = ChangeAuditJdbcAutoConfiguration.class)
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@ConditionalOnProperty(prefix = ChangeAuditProperties.PREFIX + ".metrics", name = "enabled", matchIfMissing = true)
public class ChangeAuditMicrometerAutoConfiguration {

    @Bean
    @ConditionalOnBean({MeterRegistry.class, JdbcChangeRecorder.class})
    @ConditionalOnMissingBean(ChangeAuditMicrometerBinder.class)
    public ChangeAuditMicrometerBinder changeAuditMicrometerBinder(JdbcChangeRecorder recorder,
                                                                  MeterRegistry meterRegistry) {
        return new ChangeAuditMicrometerBinder(recorder, meterRegistry);
    }
}
