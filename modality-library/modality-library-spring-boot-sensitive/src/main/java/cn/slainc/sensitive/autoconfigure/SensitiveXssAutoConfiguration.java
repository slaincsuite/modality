package cn.slainc.sensitive.autoconfigure;

import cn.slainc.sensitive.xss.XssSanitizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "modality.sensitive.xss", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class SensitiveXssAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(XssSanitizer.class)
    public XssSanitizer xssSanitizer() {
        return new XssSanitizer();
    }
}
