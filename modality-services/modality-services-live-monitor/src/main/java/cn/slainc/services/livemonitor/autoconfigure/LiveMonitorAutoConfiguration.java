package cn.slainc.services.livemonitor.autoconfigure;

import cn.slainc.services.livemonitor.mapper.LiveStreamMapper;
import cn.slainc.services.livemonitor.service.stream.LiveStreamProbe;
import cn.slainc.services.livemonitor.service.stream.LiveStreamService;
import cn.slainc.services.livemonitor.support.StreamUrlGuard;
import cn.slainc.services.livemonitor.web.stream.LiveStreamController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = LiveMonitorMybatisAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.live-monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LiveMonitorProperties.class)
public class LiveMonitorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(StreamUrlGuard.class)
    public StreamUrlGuard liveStreamUrlGuard() {
        return new StreamUrlGuard();
    }

    @Bean
    @ConditionalOnMissingBean(LiveStreamProbe.class)
    public LiveStreamProbe liveStreamProbe(StreamUrlGuard streamUrlGuard) {
        return new LiveStreamProbe(streamUrlGuard);
    }

    @Bean
    @ConditionalOnMissingBean(LiveStreamService.class)
    public LiveStreamService liveStreamService(LiveStreamMapper liveStreamMapper, LiveStreamProbe liveStreamProbe,
                                               StreamUrlGuard streamUrlGuard, LiveMonitorProperties properties) {
        return new LiveStreamService(liveStreamMapper, liveStreamProbe, streamUrlGuard, properties);
    }

    @Bean
    @ConditionalOnMissingBean(LiveStreamController.class)
    @ConditionalOnBean(LiveStreamService.class)
    public LiveStreamController liveStreamController(LiveStreamService liveStreamService) {
        return new LiveStreamController(liveStreamService);
    }
}
