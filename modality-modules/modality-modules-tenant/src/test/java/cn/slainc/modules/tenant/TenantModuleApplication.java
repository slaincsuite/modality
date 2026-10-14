package cn.slainc.modules.tenant;

import cn.dev33.satoken.stp.StpInterface;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.result.Result;
import cn.slainc.security.StpKit;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootConfiguration
@EnableAutoConfiguration
public class TenantModuleApplication {

    @RestController
    public static class TenantTestController {

        @GetMapping("/tenant-test/login")
        public Result<String> login() {
            StpKit.tenantUser().login(93001L);
            return Result.ok(StpKit.tenantUser().getTokenValue());
        }
    }

    // 无 Redis 环境提供内存版动态配置，验证配额调整闭环
    @Bean
    public DynamicConfigService tenantTestConfigService() {
        return new DynamicConfigService() {

            private final Map<String, String> store = new ConcurrentHashMap<>();

            @Override
            public java.util.Optional<String> findString(String key) {
                return java.util.Optional.ofNullable(store.get(key));
            }

            @Override
            public String getString(String key, String defaultValue) {
                return store.getOrDefault(key, defaultValue);
            }

            @Override
            public int getInt(String key, int defaultValue) {
                String value = store.get(key);
                return value == null ? defaultValue : Long.parseLong(value) > Integer.MAX_VALUE
                        ? Integer.MAX_VALUE : (int) Long.parseLong(value);
            }

            @Override
            public long getLong(String key, long defaultValue) {
                String value = store.get(key);
                return value == null ? defaultValue : Long.parseLong(value);
            }

            @Override
            public boolean getBoolean(String key, boolean defaultValue) {
                String value = store.get(key);
                return value == null ? defaultValue : Boolean.parseBoolean(value);
            }

            @Override
            public <T> T getObject(String key, Class<T> type) {
                return getObject(key, type, null);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getObject(String key, Class<T> type, T defaultValue) {
                return defaultValue;
            }

            @Override
            public <T> List<T> getList(String key, Class<T> elementType) {
                return List.of();
            }

            @Override
            public String getEncrypted(String key) {
                return store.get(key);
            }

            @Override
            public Map<String, String> getGroup(String group) {
                return Map.copyOf(store);
            }

            @Override
            public List<cn.slainc.core.config.DynamicConfig> listByGroup(String group) {
                return List.of();
            }

            @Override
            public void set(String key, String value) {
                store.put(key, value);
            }

            @Override
            public void set(String key, String value, String group) {
                store.put(key, value);
            }

            @Override
            public void setEncrypted(String key, String value, String group) {
                store.put(key, value);
            }

            @Override
            public void remove(String key) {
                store.remove(key);
            }

            @Override
            public void refresh(String key) {
            }

            @Override
            public void refreshAll() {
            }
        };
    }

    // 无 Redis 环境没有 SecurityPermissionCache，直接以 sa-token 的 StpInterface 提供测试权限
    @Bean
    public StpInterface tenantTestPermissionProvider() {
        return new StpInterface() {

            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return List.of("*");
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return List.of();
            }
        };
    }
}
