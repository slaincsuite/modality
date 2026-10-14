package cn.slainc.tenant.context;

import cn.slainc.core.result.ResultCode;
import cn.slainc.tenant.autoconfigure.TenantStatusCheckProperties;
import cn.slainc.tenant.domain.Tenant;
import cn.slainc.tenant.lookup.TenantService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class TenantStatusChecker {

    private static final Logger log = LoggerFactory.getLogger(TenantStatusChecker.class);

    private static final String MISSING_TENANT_MESSAGE = "无法识别租户身份，请通过请求头携带租户标识后重试";

    private static final String UNAVAILABLE_MESSAGE = "租户状态校验能力不可用，请稍后重试";

    private static final Pattern NEVER_MATCH = Pattern.compile("(?!)");

    private final TenantService tenantService;

    private final TenantStatusCheckProperties properties;

    private final Cache<Long, Tenant> cache;

    private final Map<String, Pattern> allowedPathPatterns = new ConcurrentHashMap<>();

    public TenantStatusChecker(TenantService tenantService, TenantStatusCheckProperties properties) {
        this.tenantService = tenantService;
        this.properties = properties;
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getCacheMaximumSize())
                .expireAfterWrite(properties.getCacheExpireAfterWrite())
                .build();
    }

    public TenantStatusCheckDecision check(String path, Long tenantId) {
        if (!properties.isEnabled() || isAllowedPath(path)) {
            return TenantStatusCheckDecision.allow();
        }
        if (tenantId == null) {
            if (allowMissingTenant()) {
                return TenantStatusCheckDecision.allow();
            }
            return TenantStatusCheckDecision.reject(ResultCode.FORBIDDEN, MISSING_TENANT_MESSAGE);
        }
        if (tenantService == null) {
            log.warn("未装配租户查询能力，无法校验租户 [{}] 的状态", tenantId);
            return TenantStatusCheckDecision.reject(ResultCode.FORBIDDEN, UNAVAILABLE_MESSAGE);
        }
        Tenant tenant = cache.get(tenantId, tenantService::findById);
        if (tenant == null) {
            if (allowMissingTenant()) {
                return TenantStatusCheckDecision.allow();
            }
            return TenantStatusCheckDecision.reject(ResultCode.FORBIDDEN, "租户不存在：" + tenantId);
        }
        return evaluate(tenant, tenantId);
    }

    public TenantStatusCheckDecision evaluate(Tenant tenant, Long tenantId) {
        if (tenant == null) {
            return TenantStatusCheckDecision.reject(ResultCode.FORBIDDEN, "租户不存在：" + tenantId);
        }
        if (!tenant.isUsable(LocalDateTime.now())) {
            return TenantStatusCheckDecision.reject(ResultCode.FORBIDDEN, unusableMessage(tenant));
        }
        return TenantStatusCheckDecision.allow();
    }

    public boolean isAllowedPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        for (String pattern : properties.getAllowedPaths()) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            if (matches(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    public void evict(Long tenantId) {
        if (tenantId != null) {
            cache.invalidate(tenantId);
        }
    }

    public void evictAll() {
        cache.invalidateAll();
    }

    private boolean matches(String pattern, String path) {
        Pattern compiled = allowedPathPatterns.computeIfAbsent(pattern, key -> compile(toRegex(key)));
        return compiled.matcher(path).matches();
    }

    private Pattern compile(String regex) {
        try {
            return Pattern.compile(regex);
        } catch (RuntimeException exception) {
            // 非法规则缓存为永不匹配，避免每个请求重复编译与刷日志
            log.warn("白名单路径规则不是合法正则，已忽略：[{}]", regex);
            return NEVER_MATCH;
        }
    }

    private static String toRegex(String pattern) {
        // 先用占位符收走双星：String.replace 对**按字面量逐段替换，直接换.*会得到 .[^/]*
        return pattern.replace("**", "\u0000").replace("*", "[^/]*").replace("\u0000", ".*");
    }

    private boolean allowMissingTenant() {
        return properties.getMissingTenantPolicy() == TenantStatusCheckProperties.MissingTenantPolicy.ALLOW;
    }

    private static String unusableMessage(Tenant tenant) {
        String name = tenant.getTenantName() == null || tenant.getTenantName().isBlank()
                ? String.valueOf(tenant.getId()) : tenant.getTenantName();
        if (tenant.isExpired(LocalDateTime.now())) {
            return "租户已过期：" + name;
        }
        String status = tenant.getStatus() == null ? "未设置" : tenant.getStatus().getDisplayName();
        return "租户状态不允许访问：" + name + "（" + status + "）";
    }
}
