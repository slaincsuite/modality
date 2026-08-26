package cn.slainc.tenant.lifecycle;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.tenant.entity.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public class DefaultTenantInitializer implements TenantInitializer {

    public static final String ROLES_KEY = "tenant.lifecycle.roles";

    public static final String PERMISSIONS_KEY = "tenant.lifecycle.permissions";

    public static final String MENUS_KEY = "tenant.lifecycle.menus";

    public static final String DICT_TYPES_KEY = "tenant.lifecycle.dict-types";

    public static final String STATEMENTS_KEY = "tenant.lifecycle.statements";

    private static final Logger log = LoggerFactory.getLogger(DefaultTenantInitializer.class);

    private final TenantDataTemplate template;

    private final JdbcTemplate jdbcTemplate;

    private final DynamicConfigService configService;

    public DefaultTenantInitializer(TenantDataTemplate template, JdbcTemplate jdbcTemplate,
                                    DynamicConfigService configService) {
        this.template = template == null ? new TenantDataTemplate() : template;
        this.jdbcTemplate = jdbcTemplate;
        this.configService = configService;
    }

    @Override
    public void initialize(Tenant tenant) {
        List<String> roles = effective(ROLES_KEY, template.getRoleCodes());
        List<String> permissions = effective(PERMISSIONS_KEY, template.getPermissionCodes());
        List<String> menus = effective(MENUS_KEY, template.getMenus());
        List<String> dictTypes = effective(DICT_TYPES_KEY, template.getDictTypes());
        log.info("租户 [{}] 默认数据模板：角色 {} 项、权限 {} 项、菜单 {} 项、字典 {} 项",
                tenant.getId(), roles.size(), permissions.size(), menus.size(), dictTypes.size());
        List<String> statements = effective(STATEMENTS_KEY, template.getInitStatements());
        if (statements.isEmpty()) {
            log.debug("租户 [{}] 未配置初始化脚本，跳过数据初始化", tenant.getId());
            return;
        }
        if (jdbcTemplate == null) {
            log.warn("未配置 JdbcTemplate，跳过租户 [{}] 的 {} 条初始化脚本", tenant.getId(), statements.size());
            return;
        }
        for (String statement : statements) {
            jdbcTemplate.execute(render(statement, tenant));
        }
        log.info("租户 [{}] 完成 {} 条初始化脚本", tenant.getId(), statements.size());
    }

    @Override
    public int order() {
        return 100;
    }

    public static String render(String statement, Tenant tenant) {
        if (statement == null) {
            return null;
        }
        return statement
                .replace("{tenantId}", String.valueOf(tenant.getId()))
                .replace("{tenantCode}", String.valueOf(tenant.getTenantCode()))
                .replace("{tenantName}", String.valueOf(tenant.getTenantName()));
    }

    private List<String> effective(String key, List<String> fallback) {
        if (configService == null) {
            return fallback;
        }
        List<String> configured = configService.getList(key, String.class);
        return configured == null || configured.isEmpty() ? fallback : configured;
    }
}
