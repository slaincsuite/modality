package cn.slainc.modules.open.autoconfigure;

import cn.slainc.modules.open.OpenKeys;
import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration(afterName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@AutoConfigureBefore(name = "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnClass(name = "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.open", name = "enabled", matchIfMissing = true)
public class OpenMybatisAutoConfiguration {

    private static final List<String> GLOBAL_TABLES = List.of(OpenKeys.APP_TABLE, OpenKeys.SCOPE_TABLE,
            OpenKeys.SCOPE_GROUP_TABLE, OpenKeys.CONSENT_TABLE, OpenKeys.CREDENTIAL_TABLE, OpenKeys.APP_LOG_TABLE,
            OpenKeys.WEBHOOK_SUBSCRIPTION_TABLE, OpenKeys.WEBHOOK_DELIVERY_TABLE);

    @Bean
    @ConditionalOnMissingBean(OpenTableWhitelist.class)
    public OpenTableWhitelist openTableWhitelist(ObjectProvider<MybatisProperties> mybatisProperties,
                                                 ObjectProvider<DefaultTenantLineHandler> tenantLineHandler) {
        return new OpenTableWhitelist(GLOBAL_TABLES, mybatisProperties.getIfAvailable(),
                tenantLineHandler.getIfAvailable());
    }
}
