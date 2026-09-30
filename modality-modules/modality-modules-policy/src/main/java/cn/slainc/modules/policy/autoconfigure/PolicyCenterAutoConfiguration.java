package cn.slainc.modules.policy.autoconfigure;

import cn.slainc.modules.policy.service.PolicyBindingPortalService;
import cn.slainc.modules.policy.service.PolicyManageService;
import cn.slainc.modules.policy.service.PolicyTestCaseService;
import cn.slainc.modules.policy.service.PolicyTransferService;
import cn.slainc.modules.policy.store.PolicyMetaStore;
import cn.slainc.modules.policy.store.PolicyTestCaseStore;
import cn.slainc.modules.policy.web.PolicyBindingPortalController;
import cn.slainc.modules.policy.web.PolicyManageController;
import cn.slainc.modules.policy.web.PolicySimulateController;
import cn.slainc.modules.policy.web.PolicyTestCaseController;
import cn.slainc.modules.policy.web.PolicyTransferController;
import cn.slainc.modules.upms.service.PolicyBindingService;
import cn.slainc.policy.admin.PolicyAdminService;
import cn.slainc.policy.admin.PolicyDiffService;
import cn.slainc.policy.simulate.PolicySimulator;
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
        PolicyTransferController.class, PolicyBindingPortalController.class})
public class PolicyCenterAutoConfiguration {

    @Bean(initMethod = "init")
    @ConditionalOnMissingBean(PolicyCenterSchemaInitializer.class)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "modality.policy-center", name = "auto-create-table", matchIfMissing = true)
    public PolicyCenterSchemaInitializer policyCenterSchemaInitializer(DataSource dataSource) {
        return new PolicyCenterSchemaInitializer(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyMetaStore policyMetaStore(JdbcTemplate jdbcTemplate) {
        return new PolicyMetaStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyTestCaseStore policyTestCaseStore(JdbcTemplate jdbcTemplate) {
        return new PolicyTestCaseStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyManageService policyManageService(PolicyAdminService adminService, PolicyMetaStore metaStore,
                                                   PolicyDiffService diffService) {
        return new PolicyManageService(adminService, metaStore, diffService);
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
            ObjectProvider<PolicyBindingService> bindingService, PolicyManageService manageService) {
        return new PolicyBindingPortalService(bindingService, manageService);
    }
}
