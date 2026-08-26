package cn.slainc.tenant.isolation;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantIsolationDispatcherTest {

    private final StubHandler row = new StubHandler(TenantIsolationStrategy.ROW, 100);

    private final StubHandler schema = new StubHandler(TenantIsolationStrategy.SCHEMA, 200);

    private final StubHandler database = new StubHandler(TenantIsolationStrategy.DATABASE, 300);

    @Test
    @DisplayName("按策略分发到对应处理器，处理器按 order 排序")
    void dispatchesByStrategy() {
        TenantIsolationDispatcher dispatcher = dispatcher(null);
        TenantInfo rowTenant = TenantInfo.of(1L, "row", TenantIsolationStrategy.ROW);
        TenantInfo schemaTenant = TenantInfo.of(2L, "schema", TenantIsolationStrategy.SCHEMA);
        TenantInfo databaseTenant = TenantInfo.of(3L, "database", TenantIsolationStrategy.DATABASE);

        dispatcher.apply(rowTenant);
        dispatcher.apply(schemaTenant);
        dispatcher.apply(databaseTenant);
        dispatcher.remove(schemaTenant);

        assertEquals(List.of("row"), row.applied);
        assertEquals(List.of("schema"), schema.applied);
        assertEquals(List.of("schema"), schema.removed);
        assertEquals(List.of("database"), database.applied);
        assertEquals(List.of(TenantIsolationStrategy.ROW, TenantIsolationStrategy.SCHEMA,
                TenantIsolationStrategy.DATABASE), dispatcher.handlers().stream()
                .map(handler -> ((StubHandler) handler).strategy).toList());
        assertSame(schema, dispatcher.handler(TenantIsolationStrategy.SCHEMA));
    }

    @Test
    @DisplayName("策略为空时使用热配置，其次使用默认策略")
    void resolvesStrategyFromConfigThenDefault() {
        InMemoryConfigService config = new InMemoryConfigService();
        TenantIsolationDispatcher dispatcher = dispatcher(config);
        TenantInfo tenant = TenantInfo.of(9L, "acme", null);

        assertEquals(TenantIsolationStrategy.ROW, dispatcher.resolveStrategy(tenant));

        config.values.put("tenant.isolation.strategy", "SCHEMA");
        assertEquals(TenantIsolationStrategy.SCHEMA, dispatcher.resolveStrategy(tenant));

        config.values.put("tenant.isolation.strategy.9", "DATABASE");
        assertEquals(TenantIsolationStrategy.DATABASE, dispatcher.resolveStrategy(tenant));
        assertEquals("库级隔离", dispatcher.resolveStrategy(tenant).getDisplayName());
    }

    @Test
    @DisplayName("租户自身指定的策略优先于热配置")
    void tenantStrategyWins() {
        InMemoryConfigService config = new InMemoryConfigService();
        config.values.put("tenant.isolation.strategy", "DATABASE");
        TenantIsolationDispatcher dispatcher = dispatcher(config);
        assertEquals(TenantIsolationStrategy.SCHEMA,
                dispatcher.resolveStrategy(TenantInfo.of(9L, "acme", TenantIsolationStrategy.SCHEMA)));
    }

    @Test
    @DisplayName("没有匹配处理器时抛出中文业务异常")
    void failsWithoutMatchingHandler() {
        TenantIsolationDispatcher dispatcher = new TenantIsolationDispatcher(List.of(row),
                new TenantIsolationStrategyResolver(TenantIsolationStrategy.ROW, null));
        BizException exception = assertThrows(BizException.class,
                () -> dispatcher.handler(TenantIsolationStrategy.SCHEMA));
        assertTrue(exception.getMessage().contains("没有可用的租户隔离处理器"), exception.getMessage());
    }

    private TenantIsolationDispatcher dispatcher(DynamicConfigService config) {
        return new TenantIsolationDispatcher(List.of(database, row, schema),
                new TenantIsolationStrategyResolver(TenantIsolationStrategy.ROW, config));
    }

    private static final class StubHandler implements TenantIsolationHandler {

        private final TenantIsolationStrategy strategy;

        private final int order;

        private final List<String> applied = new ArrayList<>();

        private final List<String> removed = new ArrayList<>();

        private StubHandler(TenantIsolationStrategy strategy, int order) {
            this.strategy = strategy;
            this.order = order;
        }

        @Override
        public boolean supports(TenantIsolationStrategy strategy) {
            return this.strategy == strategy;
        }

        @Override
        public void apply(TenantInfo tenant) {
            applied.add(strategy.name().toLowerCase());
        }

        @Override
        public void remove(TenantInfo tenant) {
            removed.add(strategy.name().toLowerCase());
        }

        @Override
        public int order() {
            return order;
        }
    }

    private static final class InMemoryConfigService implements DynamicConfigService {

        private final java.util.Map<String, String> values = new java.util.HashMap<>();

        @Override
        public Optional<String> findString(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public String getString(String key, String defaultValue) {
            return values.getOrDefault(key, defaultValue);
        }

        @Override
        public int getInt(String key, int defaultValue) {
            return Integer.parseInt(values.getOrDefault(key, String.valueOf(defaultValue)));
        }

        @Override
        public long getLong(String key, long defaultValue) {
            return Long.parseLong(values.getOrDefault(key, String.valueOf(defaultValue)));
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            return Boolean.parseBoolean(values.getOrDefault(key, String.valueOf(defaultValue)));
        }

        @Override
        public <T> T getObject(String key, Class<T> type) {
            return null;
        }

        @Override
        public <T> T getObject(String key, Class<T> type, T defaultValue) {
            return defaultValue;
        }

        @Override
        public <T> List<T> getList(String key, Class<T> elementType) {
            return List.of();
        }

        @Override
        public String getEncrypted(String key) {
            return null;
        }

        @Override
        public java.util.Map<String, String> getGroup(String group) {
            return java.util.Map.of();
        }

        @Override
        public List<cn.slainc.core.config.DynamicConfig> listByGroup(String group) {
            return List.of();
        }

        @Override
        public void set(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void set(String key, String value, String group) {
            values.put(key, value);
        }

        @Override
        public void setEncrypted(String key, String value, String group) {
            values.put(key, value);
        }

        @Override
        public void remove(String key) {
            values.remove(key);
        }

        @Override
        public void refresh(String key) {
        }

        @Override
        public void refreshAll() {
        }
    }
}
