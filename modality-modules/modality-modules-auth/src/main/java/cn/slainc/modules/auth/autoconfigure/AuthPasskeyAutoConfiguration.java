package cn.slainc.modules.auth.autoconfigure;

import cn.slainc.modules.auth.service.passkey.PasskeyService;
import cn.slainc.modules.auth.service.user.TargetUserGuard;
import cn.slainc.modules.auth.store.PasskeyStore;
import cn.slainc.modules.auth.web.passkey.PasskeyManageController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration(after = AuthSecurityPolicyAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnBean({JdbcTemplate.class, TargetUserGuard.class})
@ConditionalOnProperty(prefix = "modality.auth.passkey", name = "enabled", matchIfMissing = true)
@Import(PasskeyManageController.class)
public class AuthPasskeyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PasskeyStore passkeyStore(JdbcTemplate jdbcTemplate) {
        return new PasskeyStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public PasskeyService passkeyService(PasskeyStore store) {
        return new PasskeyService(store);
    }
}
