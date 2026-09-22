package cn.slainc.modules.upms.autoconfigure;

import cn.slainc.modules.upms.config.UpmsProperties;
import cn.slainc.modules.upms.datascope.RoleBasedDataScopeProvider;
import cn.slainc.modules.upms.excel.OperationLogExportService;
import cn.slainc.modules.upms.excel.PermissionExcelService;
import cn.slainc.modules.upms.excel.UserExcelService;
import cn.slainc.modules.upms.log.LoginRecordCollector;
import cn.slainc.modules.upms.log.LoginRecordService;
import cn.slainc.modules.upms.log.OperationLogService;
import cn.slainc.modules.upms.log.OperationLogStore;
import cn.slainc.modules.upms.mapper.AccessControlMapper;
import cn.slainc.modules.upms.mapper.DeptMapper;
import cn.slainc.modules.upms.mapper.PermissionMapper;
import cn.slainc.modules.upms.mapper.PolicyBindingMapper;
import cn.slainc.modules.upms.mapper.RoleDataScopeMapper;
import cn.slainc.modules.upms.mapper.RoleMapper;
import cn.slainc.modules.upms.mapper.RolePermissionMapper;
import cn.slainc.modules.upms.mapper.SystemRegistryMapper;
import cn.slainc.modules.upms.mapper.UserGroupMapper;
import cn.slainc.modules.upms.mapper.UserGroupMemberMapper;
import cn.slainc.modules.upms.mapper.UserLoginRecordMapper;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.modules.upms.mapper.UserOperationLogMapper;
import cn.slainc.modules.upms.mapper.UserRoleMapper;
import cn.slainc.modules.upms.security.UpmsAccountAuthProvider;
import cn.slainc.modules.upms.security.UpmsAccountPasswordUpdater;
import cn.slainc.modules.upms.security.UpmsPermissionCacheInvalidator;
import cn.slainc.modules.upms.security.UpmsPermissionProvider;
import cn.slainc.modules.upms.service.AccessControlService;
import cn.slainc.modules.upms.service.DeptService;
import cn.slainc.modules.upms.service.MenuService;
import cn.slainc.modules.upms.service.PermissionService;
import cn.slainc.modules.upms.service.PolicyBindingService;
import cn.slainc.modules.upms.service.RoleService;
import cn.slainc.modules.upms.service.SystemRegistryService;
import cn.slainc.modules.upms.service.UserGroupService;
import cn.slainc.modules.upms.service.UserPasswordService;
import cn.slainc.modules.upms.service.UserService;
import cn.slainc.modules.upms.service.UserStatsService;
import cn.slainc.modules.upms.web.AccessControlController;
import cn.slainc.modules.upms.web.DeptManageController;
import cn.slainc.modules.upms.web.LoginRecordController;
import cn.slainc.modules.upms.web.MenuManageController;
import cn.slainc.modules.upms.web.OperationLogController;
import cn.slainc.modules.upms.web.PermissionManageController;
import cn.slainc.modules.upms.web.PolicyBindingController;
import cn.slainc.modules.upms.web.RoleManageController;
import cn.slainc.modules.upms.web.SystemRegistryController;
import cn.slainc.modules.upms.web.UserGroupManageController;
import cn.slainc.modules.upms.web.UserManageController;
import cn.slainc.modules.upms.web.UserStatsController;
import cn.slainc.modules.upms.web.UpmsContextBindingFilter;
import cn.slainc.mybatis.datascope.DataScopeProvider;
import cn.slainc.policy.admin.PolicyAdminService;
import cn.slainc.security.cache.SecurityPermissionCache;
import cn.slainc.security.login.CurrentRequestReader;
import cn.slainc.security.password.PasswordPolicyService;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.AccountPasswordUpdater;
import cn.slainc.security.spi.SecurityPermissionProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

// 必须先于 security 与 mybatis 库装配：三个 SPI 与数据权限 provider 在此抢先注册，宿主自定义 Bean 仍可整体覆盖
@AutoConfiguration(beforeName = {"cn.slainc.security.autoconfigure.SecurityAutoConfiguration",
        "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration"})
@ConditionalOnProperty(prefix = "modality.upms", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(UpmsProperties.class)
@Import({UserManageController.class, RoleManageController.class, PermissionManageController.class,
        MenuManageController.class, DeptManageController.class, UserGroupManageController.class,
        SystemRegistryController.class, AccessControlController.class, PolicyBindingController.class,
        OperationLogController.class, LoginRecordController.class, UserStatsController.class})
public class UpmsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityPermissionProvider.class)
    public UpmsPermissionProvider upmsPermissionProvider(UserMapper userMapper, RoleService roleService,
                                                         SystemRegistryService systemRegistryService,
                                                         AccessControlService accessControlService) {
        return new UpmsPermissionProvider(userMapper, roleService, systemRegistryService, accessControlService);
    }

    @Bean
    @ConditionalOnMissingBean(AccountAuthProvider.class)
    public UpmsAccountAuthProvider upmsAccountAuthProvider(UserMapper userMapper) {
        return new UpmsAccountAuthProvider(userMapper);
    }

    @Bean
    @ConditionalOnMissingBean(AccountPasswordUpdater.class)
    public UpmsAccountPasswordUpdater upmsAccountPasswordUpdater(UserMapper userMapper) {
        return new UpmsAccountPasswordUpdater(userMapper);
    }

    @Bean
    @ConditionalOnMissingBean(DataScopeProvider.class)
    public RoleBasedDataScopeProvider roleBasedDataScopeProvider(ObjectProvider<RoleService> roleService,
                                                                 ObjectProvider<cn.slainc.modules.upms.datascope.RoleDataScopeMapperBridge> bridge,
                                                                 ObjectProvider<DeptService> deptService,
                                                                 ObjectProvider<UserMapper> userMapper) {
        return new RoleBasedDataScopeProvider(roleService, bridge, deptService, userMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public cn.slainc.modules.upms.datascope.RoleDataScopeMapperBridge roleDataScopeMapperBridge(
            RoleDataScopeMapper mapper) {
        return new cn.slainc.modules.upms.datascope.RoleDataScopeMapperBridge(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public UpmsPermissionCacheInvalidator upmsPermissionCacheInvalidator(
            ObjectProvider<SecurityPermissionCache> cache, ObjectProvider<UserMapper> userMapper) {
        return new UpmsPermissionCacheInvalidator(cache, userMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RoleService roleService(RoleMapper roleMapper, RolePermissionMapper rolePermissionMapper,
                                   UserRoleMapper userRoleMapper, RoleDataScopeMapper roleDataScopeMapper,
                                   PermissionMapper permissionMapper,
                                   UpmsPermissionCacheInvalidator cacheInvalidator) {
        return new RoleService(roleMapper, rolePermissionMapper, userRoleMapper, roleDataScopeMapper,
                permissionMapper, cacheInvalidator);
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionService permissionService(PermissionMapper permissionMapper,
                                               RolePermissionMapper rolePermissionMapper,
                                               UserRoleMapper userRoleMapper,
                                               UpmsPermissionCacheInvalidator cacheInvalidator) {
        return new PermissionService(permissionMapper, rolePermissionMapper, userRoleMapper, cacheInvalidator);
    }

    @Bean
    @ConditionalOnMissingBean
    public MenuService menuService(cn.slainc.modules.upms.mapper.MenuMapper menuMapper) {
        return new MenuService(menuMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public DeptService deptService(DeptMapper deptMapper, UserMapper userMapper) {
        return new DeptService(deptMapper, userMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserGroupService userGroupService(UserGroupMapper groupMapper,
                                             cn.slainc.modules.upms.mapper.UserGroupMemberMapper memberMapper,
                                             UserMapper userMapper) {
        return new UserGroupService(groupMapper, memberMapper, userMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AccessControlService accessControlService(AccessControlMapper accessControlMapper,
                                                     SystemRegistryMapper systemRegistryMapper,
                                                     UpmsPermissionCacheInvalidator cacheInvalidator) {
        return new AccessControlService(accessControlMapper, systemRegistryMapper, cacheInvalidator);
    }

    @Bean
    @ConditionalOnMissingBean
    public SystemRegistryService systemRegistryService(SystemRegistryMapper systemMapper,
                                                       AccessControlService accessControlService,
                                                       UpmsPermissionCacheInvalidator cacheInvalidator) {
        return new SystemRegistryService(systemMapper, accessControlService, cacheInvalidator);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserService userService(UserMapper userMapper, UserRoleMapper userRoleMapper,
                                   UserGroupMemberMapper groupMemberMapper, AccessControlMapper accessControlMapper,
                                   PolicyBindingMapper policyBindingMapper,
                                   UserLoginRecordMapper loginRecordMapper, UpmsProperties properties,
                                   UpmsPermissionCacheInvalidator cacheInvalidator,
                                   ApplicationEventPublisher publisher) {
        return new UserService(userMapper, userRoleMapper, groupMemberMapper, accessControlMapper,
                policyBindingMapper, loginRecordMapper, properties, cacheInvalidator, publisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserPasswordService userPasswordService(UserService userService, UserMapper userMapper,
                                                   ObjectProvider<PasswordPolicyService> passwordPolicy,
                                                   ObjectProvider<AccountPasswordUpdater> passwordUpdater,
                                                   UpmsPermissionCacheInvalidator cacheInvalidator) {
        return new UserPasswordService(userService, userMapper, passwordPolicy.getIfAvailable(),
                passwordUpdater.getIfAvailable(), cacheInvalidator);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyBindingService policyBindingService(PolicyBindingMapper bindingMapper,
                                                     ObjectProvider<PolicyAdminService> policyAdminService) {
        return new PolicyBindingService(bindingMapper, policyAdminService);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoginRecordCollector loginRecordCollector(UserLoginRecordMapper loginRecordMapper, UserMapper userMapper,
                                                     ObjectProvider<CurrentRequestReader> requestReader) {
        return new LoginRecordCollector(loginRecordMapper, userMapper, requestReader.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationLogStore operationLogStore(UserOperationLogMapper operationLogMapper) {
        return new OperationLogStore(operationLogMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoginRecordService loginRecordService(UserLoginRecordMapper loginRecordMapper) {
        return new LoginRecordService(loginRecordMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationLogService operationLogService(UserOperationLogMapper operationLogMapper) {
        return new OperationLogService(operationLogMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserStatsService userStatsService(UserMapper userMapper, UserRoleMapper userRoleMapper,
                                             RoleMapper roleMapper, UserLoginRecordMapper loginRecordMapper,
                                             UpmsProperties properties) {
        return new UserStatsService(userMapper, userRoleMapper, roleMapper, loginRecordMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserExcelService userExcelService(UserService userService) {
        return new UserExcelService(userService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionExcelService permissionExcelService(PermissionService permissionService) {
        return new PermissionExcelService(permissionService);
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationLogExportService operationLogExportService(OperationLogService operationLogService) {
        return new OperationLogExportService(operationLogService);
    }

    // 紧随 Sa-Token 过滤器之后绑定 UserContext：数据权限与用户菜单依赖请求内的用户/角色/权限上下文
    // StpInterface 必须运行期解析：本配置先于 security 装配，条件期直查会判 false
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
    public org.springframework.boot.web.servlet.FilterRegistrationBean<UpmsContextBindingFilter> upmsContextBindingFilter(
            ObjectProvider<cn.dev33.satoken.stp.StpInterface> stpInterface) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(
                new UpmsContextBindingFilter(stpInterface));
        registration.setOrder(-5900);
        return registration;
    }
}
