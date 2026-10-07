package cn.slainc.policy.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.policy.admin.PolicyAdminService;
import cn.slainc.policy.admin.PolicyBindingService;
import cn.slainc.policy.admin.PolicyDiffService;
import cn.slainc.policy.aspect.PolicyAspect;
import cn.slainc.policy.bridge.PolicySecurityBridge;
import cn.slainc.policy.config.PolicyProperties;
import cn.slainc.policy.engine.PolicyEngine;
import cn.slainc.policy.simulate.PolicySimulator;
import cn.slainc.policy.storage.InMemoryPolicyBindingStore;
import cn.slainc.policy.storage.InMemoryPolicyStore;
import cn.slainc.policy.storage.PolicyBindingStore;
import cn.slainc.policy.storage.PolicyStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration(after = PolicyJdbcAutoConfiguration.class,
        beforeName = "cn.slainc.security.autoconfigure.SecurityAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.policy", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(PolicyProperties.class)
public class PolicyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PolicyStore.class)
    public InMemoryPolicyStore policyStore() {
        return new InMemoryPolicyStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyEngine policyEngine(PolicyStore policyStore, ObjectProvider<DynamicConfigService> configService,
                                     PolicyProperties properties) {
        return new PolicyEngine(policyStore, configService, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyAdminService policyAdminService(PolicyStore policyStore, PolicyEngine policyEngine,
                                                 PolicyProperties properties,
                                                 ObjectProvider<DynamicConfigService> configService,
                                                 ObjectProvider<ApplicationEventPublisher> eventPublisher) {
        return new PolicyAdminService(policyStore, policyEngine, properties, configService,
                eventPublisher.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(PolicyBindingStore.class)
    public InMemoryPolicyBindingStore policyBindingStore() {
        return new InMemoryPolicyBindingStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyBindingService policyBindingService(PolicyBindingStore bindingStore,
                                                     PolicyAdminService policyAdminService) {
        return new PolicyBindingService(bindingStore, policyAdminService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyDiffService policyDiffService() {
        return new PolicyDiffService();
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicySimulator policySimulator(PolicyEngine policyEngine) {
        return new PolicySimulator(policyEngine);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication
    @ConditionalOnClass(name = "cn.dev33.satoken.stp.StpLogic")
    static class PolicyAspectConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public PolicyAspect policyAspect(PolicyEngine policyEngine) {
            return new PolicyAspect(policyEngine);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "cn.slainc.security.spi.SecurityPolicyProvider")
    static class PolicySecurityBridgeConfiguration {

        @Bean
        @ConditionalOnMissingBean(cn.slainc.security.spi.SecurityPolicyProvider.class)
        @ConditionalOnProperty(prefix = "modality.policy", name = "security-bridge-enabled", matchIfMissing = true)
        public PolicySecurityBridge policySecurityBridge(PolicyEngine policyEngine) {
            return new PolicySecurityBridge(policyEngine);
        }
    }
}
