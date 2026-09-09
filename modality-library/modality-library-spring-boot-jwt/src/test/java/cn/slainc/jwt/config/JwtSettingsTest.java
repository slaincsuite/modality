package cn.slainc.jwt.config;

import cn.slainc.jwt.MapDynamicConfigService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSettingsTest {

    @Test
    void 四种TokenType映射到各自的动态TTL键() {
        MapDynamicConfigService dynamic = new MapDynamicConfigService();
        dynamic.put(JwtSettings.KEY_TTL_ACCESS, "111");
        dynamic.put(JwtSettings.KEY_TTL_REFRESH, "222");
        dynamic.put(JwtSettings.KEY_TTL_ID, "333");
        dynamic.put(JwtSettings.KEY_TTL_ONE_SHOT, "444");
        JwtSettings settings = new JwtSettings(new JwtProperties(), (cn.slainc.core.config.DynamicConfigService) dynamic);

        assertThat(settings.ttl(cn.slainc.jwt.issue.TokenType.ACCESS)).isEqualTo(Duration.ofSeconds(111));
        assertThat(settings.ttl(cn.slainc.jwt.issue.TokenType.REFRESH)).isEqualTo(Duration.ofSeconds(222));
        assertThat(settings.ttl(cn.slainc.jwt.issue.TokenType.ID_TOKEN)).isEqualTo(Duration.ofSeconds(333));
        assertThat(settings.ttl(cn.slainc.jwt.issue.TokenType.ONE_SHOT)).isEqualTo(Duration.ofSeconds(444));
    }

    @Test
    void 无动态配置时回落到properties默认值() {
        JwtProperties properties = new JwtProperties();
        JwtSettings settings = new JwtSettings(properties, (cn.slainc.core.config.DynamicConfigService) null);
        assertThat(settings.ttl(cn.slainc.jwt.issue.TokenType.ACCESS)).isEqualTo(Duration.ofHours(2));
        assertThat(settings.ttl(cn.slainc.jwt.issue.TokenType.REFRESH)).isEqualTo(Duration.ofDays(30));
        assertThat(settings.ttl(cn.slainc.jwt.issue.TokenType.ID_TOKEN)).isEqualTo(Duration.ofMinutes(10));
        assertThat(settings.ttl(cn.slainc.jwt.issue.TokenType.ONE_SHOT)).isEqualTo(Duration.ofMinutes(5));
        assertThat(settings.clockSkewSeconds()).isEqualTo(60);
        assertThat(settings.maxTokenLength()).isEqualTo(4096);
        assertThat(settings.jwksCacheSeconds()).isEqualTo(600);
    }

    @Test
    void spec显式ttl优先于动态与默认() {
        JwtSettings settings = new JwtSettings(new JwtProperties(), new MapDynamicConfigService());
        assertThat(settings.ttl(cn.slainc.jwt.issue.TokenType.ACCESS, Duration.ofSeconds(77)))
                .isEqualTo(Duration.ofSeconds(77));
        assertThat(settings.ttl(cn.slainc.jwt.issue.TokenType.ACCESS, null))
                .isEqualTo(Duration.ofHours(2));
    }

    @Test
    void clockSkew与maxTokenLength与jwks缓存秒数动态热读() {
        MapDynamicConfigService dynamic = new MapDynamicConfigService();
        JwtSettings settings = new JwtSettings(new JwtProperties(), (cn.slainc.core.config.DynamicConfigService) dynamic);
        assertThat(settings.clockSkewSeconds()).isEqualTo(60);

        dynamic.set(JwtSettings.KEY_CLOCK_SKEW_SECONDS, "0");
        dynamic.set(JwtSettings.KEY_MAX_TOKEN_LENGTH, "512");
        dynamic.set(JwtSettings.KEY_JWKS_CACHE_SECONDS, "3");
        assertThat(settings.clockSkewSeconds()).isZero();
        assertThat(settings.maxTokenLength()).isEqualTo(512);
        assertThat(settings.jwksCacheSeconds()).isEqualTo(3);
    }

    @Test
    void TTL最小值保护避免立即可用过期() {
        MapDynamicConfigService dynamic = new MapDynamicConfigService();
        dynamic.set(JwtSettings.KEY_TTL_ACCESS, "0");
        JwtSettings settings = new JwtSettings(new JwtProperties(), (cn.slainc.core.config.DynamicConfigService) dynamic);
        assertThat(settings.ttl(cn.slainc.jwt.issue.TokenType.ACCESS)).isEqualTo(Duration.ofSeconds(1));
    }
}
