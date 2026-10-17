package cn.slainc.modules.upms.autoconfigure;

import cn.slainc.modules.upms.config.UpmsProperties;
import cn.slainc.modules.upms.datascope.RoleBasedDataScopeProvider;
import cn.slainc.modules.upms.excel.OperationLogExportService;
import cn.slainc.modules.upms.excel.PermissionExcelService;
import cn.slainc.modules.upms.excel.UserExcelService;
import cn.slainc.modules.upms.log.LoginRecordCollector;
import cn.slainc.modules.upms.log.LoginRecordService;
import cn.slainc.modules.upms.log.LogoutRecordCollector;
import cn.slainc.modules.upms.log.OperationLogService;
import cn.slainc.modules.upms.log.OperationLogStore;
import cn.slainc.modules.upms.mapper.AccessControlMapper;
import cn.slainc.modules.upms.mapper.DeptMapper;
import cn.slainc.modules.upms.mapper.PermissionMapper;
import cn.slainc.modules.upms.mapper.PolicyBindingMapper;
import cn.slainc.modules.upms.mapper.RoleDataScopeMapper;
import cn.slainc.modules.upms.mapper.RoleGroupMapper;
import cn.slainc.modules.upms.mapper.RoleMapper;
import cn.slainc.modules.upms.mapper.RolePermissionMapper;
import cn.slainc.modules.upms.mapper.SystemRegistryMapper;
import cn.slainc.modules.upms.mapper.UserGroupMapper;
import cn.slainc.modules.upms.mapper.UserGroupMemberMapper;
import cn.slainc.modules.upms.mapper.UserLoginRecordMapper;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.modules.upms.mapper.UserOperationLogMapper;
import cn.slainc.modules.upms.mapper.UserPermissionBoundaryMapper;
import cn.slainc.modules.upms.mapper.UserPreferencesMapper;
import cn.slainc.modules.upms.mapper.UserRoleMapper;
import cn.slainc.modules.upms.mapper.UserSettingsHistoryMapper;
import cn.slainc.modules.upms.mapper.UserSettingsMapper;
import cn.slainc.modules.upms.mapper.FavoriteMapper;
import cn.slainc.modules.upms.mapper.RecentVisitMapper;
import cn.slainc.modules.upms.security.UpmsAccountAdminPort;
import cn.slainc.modules.upms.security.UpmsAccountAuthProvider;
import cn.slainc.modules.upms.security.UpmsAccountPasswordUpdater;
import cn.slainc.modules.upms.security.UpmsAccountRestrictionPort;
import cn.slainc.modules.upms.security.UpmsPermissionCacheInvalidator;
import cn.slainc.modules.upms.security.UpmsPermissionProvider;
import cn.slainc.modules.upms.security.PolicyPermissionCacheListener;
import cn.slainc.modules.upms.service.access.AccessControlService;
import cn.slainc.modules.upms.service.dept.DeptService;
import cn.slainc.modules.upms.service.menu.MenuService;
import cn.slainc.modules.upms.service.self.FavoriteService;
import cn.slainc.modules.upms.service.self.RecentVisitService;
import cn.slainc.modules.upms.service.permission.PermissionService;
import cn.slainc.modules.upms.service.permission.PermissionCodeCatalog;
import cn.slainc.modules.upms.service.permission.PermissionDelegationGuard;
import cn.slainc.modules.upms.service.role.RoleService;
import cn.slainc.modules.upms.service.policy.UpmsPolicyBindingStore;
import cn.slainc.modules.upms.service.registry.SystemRegistryService;
import cn.slainc.modules.upms.service.group.UserGroupService;
import cn.slainc.modules.upms.service.preference.UserPreferencesService;
import cn.slainc.modules.upms.service.settings.UserSettingsService;
import cn.slainc.modules.upms.service.user.UpmsUserUsageProvider;
import cn.slainc.modules.upms.service.user.UserPasswordService;
import cn.slainc.modules.upms.service.user.UserPermissionBoundaryService;
import cn.slainc.modules.upms.service.user.UserService;
import cn.slainc.modules.upms.service.user.UserStatsService;
import cn.slainc.modules.upms.web.access.AccessControlController;
import cn.slainc.modules.upms.web.dept.DeptManageController;
import cn.slainc.modules.upms.web.log.LoginRecordController;
import cn.slainc.modules.upms.web.menu.MenuManageController;
import cn.slainc.modules.upms.web.log.OperationLogController;
import cn.slainc.modules.upms.web.permission.PermissionManageController;
import cn.slainc.modules.upms.web.policy.PolicyBindingController;
import cn.slainc.modules.upms.web.role.RoleManageController;
import cn.slainc.modules.upms.web.registry.SystemRegistryController;
import cn.slainc.modules.upms.web.group.UserGroupManageController;
import cn.slainc.modules.upms.web.user.UserManageController;
import cn.slainc.modules.upms.web.user.UserStatsController;
import cn.slainc.modules.upms.web.self.FavoriteController;
import cn.slainc.modules.upms.web.self.RecentVisitController;
import cn.slainc.modules.upms.web.self.CurrentUserAccessController;
import cn.slainc.modules.upms.web.self.SelfProfileController;
import cn.slainc.modules.upms.web.self.UserPreferencesController;
import cn.slainc.modules.upms.web.settings.UserSettingsController;
import cn.slainc.modules.upms.web.user.TenantUserController;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.spi.UserUsageProvider;
import cn.slainc.mybatis.datascope.DataScopeProvider;
import cn.slainc.policy.support.PolicyBindingService;
import cn.slainc.policy.storage.PolicyBindingStore;
import cn.slainc.security.cache.SecurityPermissionCache;
import cn.slainc.security.kick.KickService;
import cn.slainc.security.login.CurrentRequestReader;
import cn.slainc.security.password.PasswordPolicyService;
import cn.slainc.security.spi.AccountAdminPort;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.AccountPasswordUpdater;
import cn.slainc.security.spi.AccountRestrictionPort;
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
import org.springframework.data.redis.core.StringRedisTemplate;

// 必须先于 security、mybatis 与 policy 库装配：SPI、数据权限 provider 与策略绑定存储在此抢先注册，宿主自定义 Bean 仍可整体覆盖
@AutoConfiguration(beforeName = {"cn.slainc.security.autoconfigure.SecurityAutoConfiguration",
        "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration",
        "cn.slainc.policy.autoconfigure.PolicyAutoConfiguration"})
@ConditionalOnProperty(prefix = "modality.upms", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(UpmsProperties.class)
@Import({UserManageController.class, RoleManageController.class, PermissionManageController.class,
        MenuManageController.class, DeptManageController.class, UserGroupManageController.class,
        SystemRegistryController.class, AccessControlController.class, PolicyBindingController.class,
        OperationLogController.class, LoginRecordController.class, UserStatsController.class,
        FavoriteController.class, RecentVisitController.class, UserSettingsController.class,
        UserPreferencesController.class, CurrentUserAccessController.class, SelfProfileController.class,
        TenantUserController.class})
public class UpmsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityPermissionProvider.class)
    public UpmsPermissionProvider upmsPermissionProvider(UserMapper userMapper, RoleService roleService,
                                                         SystemRegistryService systemRegistryService,
                                                         AccessControlService accessControlService,
                                                         UserPermissionBoundaryService permissionBoundaryService) {
        return new UpmsPermissionProvider(userMapper, roleService, systemRegistryService, accessControlService,
                permissionBoundaryService);
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
    @ConditionalOnMissingBean(AccountAdminPort.class)
    public UpmsAccountAdminPort upmsAccountAdminPort(UserService userService) {
        return new UpmsAccountAdminPort(userService);
    }

    @Bean
    @ConditionalOnMissingBean(AccountRestrictionPort.class)
    public UpmsAccountRestrictionPort upmsAccountRestrictionPort(UserService userService, UserMapper userMapper) {
        return new UpmsAccountRestrictionPort(userService, userMapper);
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
            ObjectProvider<SecurityPermissionCache> cache, ObjectProvider<UserMapper> userMapper,
            ObjectProvider<StringRedisTemplate> redis) {
        return new UpmsPermissionCacheInvalidator(cache, userMapper, redis);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyPermissionCacheListener policyPermissionCacheListener(
            UpmsPermissionCacheInvalidator invalidator) {
        return new PolicyPermissionCacheListener(invalidator);
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionDelegationGuard permissionDelegationGuard(RoleMapper roleMapper,
                                                               RolePermissionMapper rolePermissionMapper,
                                                               PermissionMapper permissionMapper) {
        return new PermissionDelegationGuard(roleMapper, rolePermissionMapper, permissionMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RoleService roleService(RoleMapper roleMapper, RolePermissionMapper rolePermissionMapper,
                                   UserRoleMapper userRoleMapper, RoleDataScopeMapper roleDataScopeMapper,
                                   RoleGroupMapper roleGroupMapper, UserGroupMapper groupMapper,
                                   UserGroupMemberMapper groupMemberMapper, UserMapper userMapper,
                                   PermissionMapper permissionMapper, PolicyBindingMapper policyBindingMapper,
                                   UpmsPermissionCacheInvalidator cacheInvalidator,
                                   PermissionDelegationGuard delegationGuard) {
        return new RoleService(roleMapper, rolePermissionMapper, userRoleMapper, roleDataScopeMapper, roleGroupMapper,
                groupMapper, groupMemberMapper, userMapper, permissionMapper, policyBindingMapper, cacheInvalidator,
                delegationGuard);
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionCodeCatalog permissionCodeCatalog(PermissionMapper permissionMapper,
                                                       UpmsProperties properties) {
        return new PermissionCodeCatalog(permissionMapper, properties.getPermissionCatalogTtlSeconds());
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionService permissionService(PermissionMapper permissionMapper,
                                               RolePermissionMapper rolePermissionMapper,
                                               UserRoleMapper userRoleMapper,
                                               RoleMapper roleMapper,
                                               cn.slainc.modules.upms.mapper.MenuMapper menuMapper,
                                               UpmsPermissionCacheInvalidator cacheInvalidator,
                                               PermissionCodeCatalog permissionCodeCatalog) {
        return new PermissionService(permissionMapper, rolePermissionMapper, userRoleMapper, roleMapper, menuMapper,
                cacheInvalidator, permissionCodeCatalog);
    }

    @Bean
    @ConditionalOnMissingBean
    public MenuService menuService(cn.slainc.modules.upms.mapper.MenuMapper menuMapper) {
        return new MenuService(menuMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public FavoriteService favoriteService(FavoriteMapper favoriteMapper) {
        return new FavoriteService(favoriteMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RecentVisitService recentVisitService(RecentVisitMapper recentVisitMapper,
                                                 cn.slainc.modules.upms.mapper.MenuMapper menuMapper) {
        return new RecentVisitService(recentVisitMapper, menuMapper);
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
                                             UserMapper userMapper, RoleMapper roleMapper,
                                             RoleGroupMapper roleGroupMapper,
                                             UpmsPermissionCacheInvalidator cacheInvalidator,
                                             PermissionDelegationGuard delegationGuard) {
        return new UserGroupService(groupMapper, memberMapper, userMapper, roleMapper, roleGroupMapper,
                cacheInvalidator, delegationGuard);
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
                                   UserGroupMemberMapper groupMemberMapper, DeptMapper deptMapper,
                                   AccessControlMapper accessControlMapper,
                                   PolicyBindingMapper policyBindingMapper,
                                   UserLoginRecordMapper loginRecordMapper,
                                   UpmsPermissionCacheInvalidator cacheInvalidator,
                                   ApplicationEventPublisher publisher,
                                   ObjectProvider<KickService> kickService,
                                   ObjectProvider<PolicyBindingService> policyBindingService,
                                   PermissionDelegationGuard delegationGuard) {
        return new UserService(userMapper, userRoleMapper, groupMemberMapper, deptMapper, accessControlMapper,
                policyBindingMapper, loginRecordMapper, cacheInvalidator, publisher,
                kickService.getIfAvailable(), policyBindingService, delegationGuard);
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
    @ConditionalOnMissingBean(PolicyBindingStore.class)
    public UpmsPolicyBindingStore upmsPolicyBindingStore(PolicyBindingMapper bindingMapper) {
        return new UpmsPolicyBindingStore(bindingMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoginRecordCollector loginRecordCollector(UserLoginRecordMapper loginRecordMapper, UserMapper userMapper,
                                                     ObjectProvider<CurrentRequestReader> requestReader) {
        return new LoginRecordCollector(loginRecordMapper, userMapper, requestReader.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public LogoutRecordCollector logoutRecordCollector(UserOperationLogMapper operationLogMapper,
                                                       UserMapper userMapper,
                                                       ObjectProvider<CurrentRequestReader> requestReader) {
        return new LogoutRecordCollector(operationLogMapper, userMapper, requestReader);
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
                                             UserOperationLogMapper operationLogMapper,
                                             UpmsProperties properties) {
        return new UserStatsService(userMapper, userRoleMapper, roleMapper, loginRecordMapper, operationLogMapper,
                properties);
    }

    @Bean
    @ConditionalOnMissingBean(UserUsageProvider.class)
    public UpmsUserUsageProvider upmsUserUsageProvider(UserMapper userMapper) {
        return new UpmsUserUsageProvider(userMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserPermissionBoundaryService userPermissionBoundaryService(
            UserPermissionBoundaryMapper boundaryMapper, PermissionMapper permissionMapper, UserMapper userMapper,
            UpmsPermissionCacheInvalidator cacheInvalidator, PermissionCodeCatalog permissionCodeCatalog) {
        return new UserPermissionBoundaryService(boundaryMapper, userMapper, cacheInvalidator,
                permissionCodeCatalog);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserPreferencesService userPreferencesService(UserPreferencesMapper preferencesMapper) {
        return new UserPreferencesService(preferencesMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserSettingsService userSettingsService(UserSettingsMapper settingsMapper,
                                                   UserSettingsHistoryMapper historyMapper, RoleMapper roleMapper,
                                                   ObjectProvider<DynamicConfigService> configService) {
        return new UserSettingsService(settingsMapper, historyMapper, roleMapper, configService);
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
}
