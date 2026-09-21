package cn.slainc.thirdauth.autoconfigure;

import cn.slainc.thirdauth.binding.JdbcThirdAccountBindingStore;
import cn.slainc.thirdauth.binding.ThirdAccountBindingStore;
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
@ConditionalOnProperty(prefix = "modality.thirdauth", name = "store", havingValue = "jdbc", matchIfMissing = true)
public class ThirdAuthJdbcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ThirdAccountBindingStore.class)
    public JdbcThirdAccountBindingStore thirdAccountBindingStore(JdbcTemplate jdbcTemplate,
                                                                 cn.slainc.thirdauth.config.ThirdAuthProperties properties) {
        return new JdbcThirdAccountBindingStore(jdbcTemplate, properties.getJdbc().getTableName(),
                properties.getJdbc().isAutoCreateTable());
    }
}
