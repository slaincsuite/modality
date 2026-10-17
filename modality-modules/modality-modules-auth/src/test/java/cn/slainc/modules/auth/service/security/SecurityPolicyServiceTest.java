package cn.slainc.modules.auth.service.security;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.modules.auth.AuthKeys;
import cn.slainc.modules.auth.policy.SecurityPolicy;
import cn.slainc.modules.auth.service.policy.SecurityPolicyService;
import cn.slainc.modules.auth.store.SecurityPolicyStore;
import cn.slainc.security.config.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityPolicyServiceTest {

    @Test
    void defaultsComeFromSecurityPropertiesAndUpdatesReachDynamicConfig() throws Exception {
        DataSource dataSource = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("securityPolicy" + System.nanoTime()).build();
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("SET MODE MySQL");
            ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.ClassPathResource("auth-schema.sql"));
        }
        SecurityProperties properties = new SecurityProperties();
        properties.getSession().setActiveTimeout(1800);
        properties.getSession().setTimeoutTenantUser(7200);
        DynamicConfigService dynamicConfig = mock(DynamicConfigService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DynamicConfigService> dynamicProvider = mock(ObjectProvider.class);
        when(dynamicProvider.getIfAvailable()).thenReturn(dynamicConfig);
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(null);
        SecurityPolicyService service = new SecurityPolicyService(
                new SecurityPolicyStore(new JdbcTemplate(dataSource)), properties, dynamicProvider, redisProvider);

        SecurityPolicyService.SessionPolicyView defaults = service.sessionPolicy();
        assertThat(defaults.idleTimeoutSeconds()).isEqualTo(1800);
        assertThat(defaults.maxSessionSeconds()).isEqualTo(7200);

        service.updateSessionPolicy(900L, 3600L);
        verify(dynamicConfig).set(AuthKeys.DYNAMIC_ACTIVE_TIMEOUT_KEY, "900", "auth");
        for (String key : AuthKeys.DYNAMIC_SESSION_TIMEOUT_KEYS) {
            verify(dynamicConfig).set(key, "3600", "auth");
        }
        SecurityPolicy stored = new SecurityPolicyStore(new JdbcTemplate(dataSource)).find();
        assertThat(stored.getIdleTimeoutSeconds()).isEqualTo(900);
        assertThat(stored.getMaxSessionSeconds()).isEqualTo(3600);
    }
}
