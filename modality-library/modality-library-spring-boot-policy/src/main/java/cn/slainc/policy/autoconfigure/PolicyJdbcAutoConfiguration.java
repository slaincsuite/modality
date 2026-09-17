package cn.slainc.policy.autoconfigure;

import cn.slainc.policy.config.PolicyProperties;
import cn.slainc.policy.storage.JdbcPolicyStore;
import cn.slainc.policy.storage.PolicyStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnBean(type = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnProperty(prefix = "modality.policy", name = "store", havingValue = "jdbc", matchIfMissing = true)
public class PolicyJdbcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PolicyStore.class)
    public JdbcPolicyStore policyStore(JdbcTemplate jdbcTemplate, PolicyProperties properties) {
        return new JdbcPolicyStore(jdbcTemplate, properties.getJdbc().getPolicyTable(),
                properties.getJdbc().getVersionTable(), properties.getJdbc().getAuditTable(),
                properties.getJdbc().isAutoCreateTable());
    }
}
