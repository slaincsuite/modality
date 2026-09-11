package cn.slainc.captcha.autoconfigure;

import cn.slainc.captcha.CaptchaMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = CaptchaAutoConfiguration.class)
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@ConditionalOnProperty(prefix = "modality.captcha.metrics", name = "enabled", matchIfMissing = true)
public class CaptchaMicrometerAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(CaptchaMicrometerBinder.class)
    public CaptchaMicrometerBinder captchaMicrometerBinder(CaptchaMetrics captchaMetrics, MeterRegistry meterRegistry) {
        return new CaptchaMicrometerBinder(captchaMetrics, meterRegistry);
    }
}
