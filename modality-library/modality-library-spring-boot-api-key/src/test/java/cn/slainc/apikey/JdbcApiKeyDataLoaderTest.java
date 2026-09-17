package cn.slainc.apikey;

import cn.dev33.satoken.apikey.model.ApiKeyModel;
import cn.slainc.apikey.config.ApiKeyProperties;
import cn.slainc.apikey.loader.JdbcApiKeyDataLoader;
import cn.slainc.apikey.mapper.ApiKeyMapper;
import cn.slainc.apikey.service.ApiKeyAdminService;
import cn.slainc.apikey.template.GuardedApiKeyTemplate;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ApiKeyTestApplication.class)
class JdbcApiKeyDataLoaderTest {

    @Autowired
    private ApiKeyAdminService adminService;

    @Autowired
    private JdbcApiKeyDataLoader loader;

    @Autowired
    private GuardedApiKeyTemplate template;

    @Autowired
    private ApiKeyMapper mapper;

    @Autowired
    private ApiKeyProperties properties;

    @Test
    void activeRecordMapsToModel() {
        ApiKeyModel issued = adminService.issue(7001L, "装载映射", List.of("order:read", "order:write"), 30L,
                Map.of("channel", "open"));
        ApiKeyModel loaded = loader.getApiKeyModelFromDatabase(properties.getNamespace(), issued.getApiKey());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getLoginId()).isEqualTo("7001");
        assertThat(loaded.getTitle()).isEqualTo("装载映射");
        assertThat(loaded.getScopes()).containsExactly("order:read", "order:write");
        assertThat(loaded.getIsValid()).isTrue();
        assertThat(loaded.getExtra("channel")).isEqualTo("open");
        assertThat(loaded.getExpiresTime()).isGreaterThan(System.currentTimeMillis());
    }

    @Test
    void expiredRecordReturnsNull() {
        ApiKeyModel issued = adminService.issue(7002L, "已过期", List.of("order:read"), 30L, null);
        mapper.update(null, new LambdaUpdateWrapper<ApiKeyRecord>()
                .eq(ApiKeyRecord::getApiKey, issued.getApiKey())
                .set(ApiKeyRecord::getExpiresTime, System.currentTimeMillis() - 1000));
        assertThat(loader.getApiKeyModelFromDatabase(properties.getNamespace(), issued.getApiKey())).isNull();
    }

    @Test
    void disabledRecordReturnsNull() {
        ApiKeyModel issued = adminService.issue(7003L, "已禁用", List.of("order:read"), 30L, null);
        adminService.disable(issued.getApiKey());
        assertThat(loader.getApiKeyModelFromDatabase(properties.getNamespace(), issued.getApiKey())).isNull();
    }

    @Test
    void deletedRecordReturnsNull() {
        ApiKeyModel issued = adminService.issue(7004L, "已删除", List.of("order:read"), 30L, null);
        adminService.delete(issued.getApiKey());
        assertThat(loader.getApiKeyModelFromDatabase(properties.getNamespace(), issued.getApiKey())).isNull();
    }

    @Test
    void unknownOrWrongNamespaceReturnsNull() {
        assertThat(loader.getApiKeyModelFromDatabase(properties.getNamespace(), "sk_not-exists")).isNull();
        ApiKeyModel issued = adminService.issue(7005L, "命名空间", List.of("order:read"), 30L, null);
        assertThat(loader.getApiKeyModelFromDatabase("other-namespace", issued.getApiKey())).isNull();
        assertThat(loader.getApiKeyModelFromDatabase(properties.getNamespace(), null)).isNull();
    }

    @Test
    void templateReloadsFromDatabaseAfterCacheMiss() {
        ApiKeyModel issued = adminService.issue(7006L, "缓存回源", List.of("order:read"), 30L, null);
        template.deleteApiKey(issued.getApiKey());
        assertThat(template.getApiKeyModelFromCache(issued.getApiKey())).isNull();
        ApiKeyModel reloaded = template.getApiKey(issued.getApiKey());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getLoginId()).isEqualTo("7006");
        assertThat(template.getApiKeyModelFromCache(issued.getApiKey())).isNotNull();
    }
}
