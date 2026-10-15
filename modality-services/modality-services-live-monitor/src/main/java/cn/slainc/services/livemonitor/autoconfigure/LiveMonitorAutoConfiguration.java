package cn.slainc.services.livemonitor.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

// 直播监控业务服务装配：仅启用配置开关，能力 Bean 随业务实现按 @ConditionalOnMissingBean 补齐
@AutoConfiguration
@ConditionalOnProperty(prefix = "modality.live-monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LiveMonitorProperties.class)
public class LiveMonitorAutoConfiguration {
}
