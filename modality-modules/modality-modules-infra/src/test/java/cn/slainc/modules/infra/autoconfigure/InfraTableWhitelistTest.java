package cn.slainc.modules.infra.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import org.junit.jupiter.api.Test;

class InfraTableWhitelistTest {

    @Test
    void registersPlatformTablesAsGlobal() {
        MybatisProperties properties = new MybatisProperties();
        DefaultTenantLineHandler handler = new DefaultTenantLineHandler(properties);

        new InfraTableWhitelist(properties, handler).afterPropertiesSet();

        assertThat(properties.getTenant().getIgnoreTables())
                .contains(InfraKeys.API_KEY_ROTATION_TABLE, InfraKeys.API_KEY_METRIC_TABLE);
        assertThat(handler.ignoreTable(InfraKeys.API_KEY_ROTATION_TABLE)).isTrue();
        assertThat(handler.ignoreTable(InfraKeys.API_KEY_METRIC_TABLE)).isTrue();
    }
}
