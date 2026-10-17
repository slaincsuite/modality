package cn.slainc.modules.policy.autoconfigure;

import cn.slainc.core.jdbc.ClasspathSchemaInitializer;
import cn.slainc.modules.policy.PolicyCenterKeys;
import cn.slainc.modules.policy.service.binding.PolicyBindingPortalService;
import cn.slainc.modules.policy.service.catalog.PolicyCatalogService;
import cn.slainc.modules.policy.service.manage.PolicyManageService;
import cn.slainc.modules.policy.service.self.PolicySelfService;
import cn.slainc.modules.policy.service.testcase.PolicyTestCaseService;
import cn.slainc.modules.policy.service.transfer.PolicyTransferService;
import cn.slainc.modules.policy.store.PolicyBoundaryStore;
import cn.slainc.modules.policy.store.PolicyMetaStore;
import cn.slainc.modules.policy.store.PolicyOriginStore;
import cn.slainc.modules.policy.store.PolicyTestCaseStore;
import cn.slainc.modules.policy.store.PolicyTemplateStore;
import cn.slainc.modules.policy.web.binding.PolicyBindingPortalController;
import cn.slainc.modules.policy.web.catalog.PolicyCatalogController;
import cn.slainc.modules.policy.web.manage.PolicyManageController;
import cn.slainc.modules.policy.web.self.PolicySelfController;
import cn.slainc.modules.policy.web.simulate.PolicySimulateController;
import cn.slainc.modules.policy.web.testcase.PolicyTestCaseController;
import cn.slainc.modules.policy.web.transfer.PolicyTransferController;
import cn.slainc.policy.support.PolicyAdminService;
import cn.slainc.policy.support.PolicyBindingService;
import cn.slainc.policy.support.PolicyDiffService;
import cn.slainc.policy.simulate.PolicySimulator;
import cn.slainc.policy.spi.PolicyGroupProvider;
import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@AutoConfiguration(afterName = {"cn.slainc.policy.autoconfigure.PolicyAutoConfiguration",
        "cn.slainc.policy.autoconfigure.PolicyJdbcAutoConfiguration"})
@ConditionalOnBean(PolicyAdminService.class)
@ConditionalOnProperty(prefix = "modality.policy-center", name = "enabled", matchIfMissing = true)
@Import({PolicyManageController.class, PolicySimulateController.class, PolicyTestCaseController.class,
        PolicyTransferController.class, PolicyBindingPortalController.class, PolicyCatalogController.class,
        PolicySelfController.class})
public class PolicyCenterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "policyCenterSchemaInitializer")
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "modality.policy-center", name = "auto-create-table", matchIfMissing = true)
    public ClasspathSchemaInitializer policyCenterSchemaInitializer(DataSource dataSource) {
        return new ClasspathSchemaInitializer(dataSource, "policy-center-schema.sql");
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyMetaStore policyMetaStore(JdbcTemplate jdbcTemplate) {
        return new PolicyMetaStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyTemplateStore policyTemplateStore(JdbcTemplate jdbcTemplate) {
        return new PolicyTemplateStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyBoundaryStore policyBoundaryStore(JdbcTemplate jdbcTemplate) {
        return new PolicyBoundaryStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyOriginStore policyOriginStore(JdbcTemplate jdbcTemplate) {
        return new PolicyOriginStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyTestCaseStore policyTestCaseStore(JdbcTemplate jdbcTemplate) {
        return new PolicyTestCaseStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyManageService policyManageService(PolicyAdminService adminService, PolicyMetaStore metaStore,
                                                   PolicyDiffService diffService,
                                                   PolicyTemplateStore templateStore,
                                                   PolicyBoundaryStore boundaryStore) {
        return new PolicyManageService(adminService, metaStore, diffService, templateStore, boundaryStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyTransferService policyTransferService(PolicyManageService manageService) {
        return new PolicyTransferService(manageService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyTestCaseService policyTestCaseService(PolicyTestCaseStore store, PolicySimulator simulator) {
        return new PolicyTestCaseService(store, simulator);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyBindingPortalService policyBindingPortalService(
            ObjectProvider<PolicyBindingService> bindingService, PolicyManageService manageService,
            PolicyBoundaryStore boundaryStore) {
        return new PolicyBindingPortalService(bindingService, manageService, boundaryStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyCatalogService policyCatalogService() {
        return new PolicyCatalogService();
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicySelfService policySelfService(PolicyManageService manageService,
                                               PolicyBindingPortalService bindingService,
                                               ObjectProvider<PolicyGroupProvider> groupProvider) {
        return new PolicySelfService(manageService, bindingService, groupProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyCenterTableWhitelist policyCenterTableWhitelist(
            ObjectProvider<MybatisProperties> mybatisProperties,
            ObjectProvider<DefaultTenantLineHandler> tenantLineHandler) {
        return new PolicyCenterTableWhitelist(
                java.util.List.of(PolicyCenterKeys.POLICY_META_TABLE, PolicyCenterKeys.POLICY_TEST_CASE_TABLE,
                        PolicyCenterKeys.POLICY_TEMPLATE_TABLE, PolicyCenterKeys.POLICY_BOUNDARY_TABLE,
                        "t_policy", "t_policy_version", "t_policy_audit"),
                mybatisProperties.getIfAvailable(), tenantLineHandler.getIfAvailable());
    }
}
