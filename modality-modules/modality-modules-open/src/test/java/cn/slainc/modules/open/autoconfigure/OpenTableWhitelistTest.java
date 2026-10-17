package cn.slainc.modules.open.autoconfigure;

import cn.slainc.modules.open.OpenKeys;
import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTableWhitelistTest {

    @Test
    void registersEveryOpenPlatformTableAsGlobal() {
        MybatisProperties properties = new MybatisProperties();
        DefaultTenantLineHandler handler = new DefaultTenantLineHandler(properties);
        List<String> tables = List.of(OpenKeys.APP_TABLE, OpenKeys.SCOPE_TABLE, OpenKeys.SCOPE_GROUP_TABLE,
                OpenKeys.CONSENT_TABLE, OpenKeys.CREDENTIAL_TABLE, OpenKeys.APP_LOG_TABLE,
                OpenKeys.WEBHOOK_SUBSCRIPTION_TABLE, OpenKeys.WEBHOOK_DELIVERY_TABLE);

        new OpenTableWhitelist(tables, properties, handler).afterPropertiesSet();

        assertThat(properties.getTenant().getIgnoreTables()).containsAll(tables);
        assertThat(handler.ignoreTable(OpenKeys.SCOPE_GROUP_TABLE)).isTrue();
        assertThat(handler.ignoreTable(OpenKeys.WEBHOOK_SUBSCRIPTION_TABLE)).isTrue();
    }
}
