package cn.slainc.monitor.alert;

import cn.slainc.monitor.autoconfigure.MonitorProperties;
import cn.slainc.monitor.support.MonitorTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRuleProviderTest {

    @Test
    @DisplayName("动态配置的 JSON 规则优先于本地配置")
    void shouldPreferDynamicRules() {
        AlertRule local = AlertRule.of("local", "本地规则", "local.metric", AlertOperator.GREATER_THAN, 1d, 1L,
                AlertLevel.INFO);
        MonitorProperties properties = new MonitorProperties();
        properties.getAlert().setRules(new java.util.ArrayList<>(List.of(local)));
        AlertRuleProvider provider = new AlertRuleProvider(MonitorTestSupport.dynamicConfig(
                MonitorTestSupport.dynamicConfigService(Map.of(AlertRuleProvider.RULES_KEY,
                        "[{\"id\":\"dynamic\",\"metric\":\"system.cpu.usage\",\"compare\":\">=\","
                                + "\"threshold\":0.8,\"durationSeconds\":30,\"level\":\"ERROR\",\"unit\":\"%\","
                                + "\"suppressSeconds\":120}]"))), properties);
        List<AlertRule> rules = provider.rules();
        assertThat(rules).hasSize(1);
        AlertRule rule = rules.get(0);
        assertThat(rule.getId()).isEqualTo("dynamic");
        assertThat(rule.getName()).isNull();
        assertThat(rule.displayName()).isEqualTo("dynamic");
        assertThat(rule.getOperator()).isEqualTo(AlertOperator.GREATER_OR_EQUAL);
        assertThat(rule.getThreshold()).isEqualTo(0.8d);
        assertThat(rule.getDurationSeconds()).isEqualTo(30L);
        assertThat(rule.getLevel()).isEqualTo(AlertLevel.ERROR);
        assertThat(rule.getUnit()).isEqualTo("%");
        assertThat(rule.resolvedSuppressSeconds(60L)).isEqualTo(120L);
        assertThat(rule.getCompare()).isEqualTo("GREATER_OR_EQUAL");
        assertThat(rule.breached(0.9d)).isTrue();
    }

    @Test
    @DisplayName("没有动态配置时使用本地配置")
    void shouldUseLocalRules() {
        MonitorProperties properties = new MonitorProperties();
        properties.getAlert().setRules(new java.util.ArrayList<>(List.of(AlertRule.of("local", "本地规则",
                "local.metric", AlertOperator.LESS_THAN, 3d, 5L, AlertLevel.INFO))));
        AlertRuleProvider provider = new AlertRuleProvider(MonitorTestSupport.dynamicConfig(
                MonitorTestSupport.dynamicConfigService(Map.of())), properties);
        assertThat(provider.rules()).extracting(AlertRule::getId).containsExactly("local");
    }

    @Test
    @DisplayName("没有显式规则时给出内置默认规则")
    void shouldProvideDefaultRules() {
        AlertRuleProvider provider = new AlertRuleProvider(MonitorTestSupport.dynamicConfig(
                MonitorTestSupport.dynamicConfigService(Map.of())), new MonitorProperties());
        assertThat(provider.rules()).extracting(AlertRule::getId)
                .containsExactly("jvm-heap-usage-high", "http-5xx-rate-high", "system-cpu-usage-high",
                        "system-disk-usage-high", "db-pool-pending-high");
        assertThat(provider.rules()).allSatisfy(rule -> assertThat(rule.getDurationSeconds()).isPositive());
    }

    @Test
    @DisplayName("关闭默认规则后返回空集合")
    void shouldDisableDefaultRules() {
        MonitorProperties properties = new MonitorProperties();
        properties.getAlert().setDefaultRules(false);
        AlertRuleProvider provider = new AlertRuleProvider(MonitorTestSupport.dynamicConfig(
                MonitorTestSupport.dynamicConfigService(Map.of())), properties);
        assertThat(provider.rules()).isEmpty();
    }

    @Test
    @DisplayName("非法规则被跳过，合法规则保留")
    void shouldSkipInvalidRules() {
        AlertRuleProvider provider = new AlertRuleProvider(MonitorTestSupport.dynamicConfig(
                MonitorTestSupport.dynamicConfigService(Map.of(AlertRuleProvider.RULES_KEY,
                        "[{\"id\":\"bad\",\"metric\":\"\"},{\"id\":\"good\",\"metric\":\"a.b\"}]"))),
                new MonitorProperties());
        assertThat(provider.rules()).extracting(AlertRule::getId).containsExactly("good");
    }

    @Test
    @DisplayName("非法 JSON 不会导致异常，回退到本地配置")
    void shouldIgnoreBrokenJson() {
        MonitorProperties properties = new MonitorProperties();
        properties.getAlert().setDefaultRules(false);
        AlertRuleProvider provider = new AlertRuleProvider(MonitorTestSupport.dynamicConfig(
                MonitorTestSupport.dynamicConfigService(Map.of(AlertRuleProvider.RULES_KEY, "not-a-json"))),
                properties);
        assertThat(provider.rules()).isEmpty();
    }
}
