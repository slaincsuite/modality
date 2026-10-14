package cn.slainc.modules.infra.web.apikey;

import cn.dev33.satoken.apikey.model.ApiKeyModel;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.apikey.support.ApiKeyAdminService;
import cn.slainc.apikey.support.ApiKeyStatsService;
import cn.slainc.modules.infra.store.ApiKeyQueryStore;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "API 密钥管理", description = "API 密钥的签发、启停、延期、轮换、删除与用量统计")
@RestController
@RequestMapping("/infra/api-keys")
public class ApiKeyManageController {

    private final ApiKeyAdminService adminService;

    private final ApiKeyStatsService statsService;

    private final ApiKeyQueryStore queryStore;

    public ApiKeyManageController(ApiKeyAdminService adminService, ApiKeyStatsService statsService,
                                  ApiKeyQueryStore queryStore) {
        this.adminService = adminService;
        this.statsService = statsService;
        this.queryStore = queryStore;
    }

    @GetMapping
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_QUERY)
    public Object list(@RequestParam String loginId) {
        return adminService.listByLoginId(loginId);
    }

    @GetMapping("/page")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_QUERY)
    public Object page(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        long offset = Math.max(0, (current - 1) * size);
        return java.util.Map.of("records", queryStore.page(status, keyword, offset, size),
                "total", queryStore.count(status, keyword));
    }

    @GetMapping(value = "/{loginId}/export.csv", produces = "text/csv;charset=UTF-8")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_QUERY)
    public String exportCsv(@PathVariable String loginId) {
        return adminService.exportCsv(loginId);
    }

    @PostMapping("/issue")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_ISSUE)
    public Object issue(@RequestBody Map<String, Object> body) {
        String loginId = stringOf(body.get("loginId"));
        String title = stringOf(body.get("title"));
        List<String> scopes = stringsOf(body.get("scopes"));
        Long ttlDays = longOf(body.get("ttlDays"));
        @SuppressWarnings("unchecked")
        Map<String, Object> extraData = (Map<String, Object>) body.get("extraData");
        Integer count = integerOf(body.get("count"));
        if (count != null && count > 1) {
            return adminService.batchIssue(loginId, title, scopes, ttlDays, extraData, count);
        }
        return adminService.issue(loginId, title, scopes, ttlDays, extraData);
    }

    @PostMapping("/{apiKey}/enable")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_MANAGE)
    public Object enable(@PathVariable String apiKey) {
        adminService.enable(apiKey);
        return Map.of("apiKey", apiKey, "status", "ENABLED");
    }

    @PostMapping("/{apiKey}/disable")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_MANAGE)
    public Object disable(@PathVariable String apiKey) {
        adminService.disable(apiKey);
        return Map.of("apiKey", apiKey, "status", "DISABLED");
    }

    @PostMapping("/{apiKey}/extend")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_MANAGE)
    public Object extend(@PathVariable String apiKey, @RequestBody Map<String, Object> body) {
        Long days = longOf(body.get("days"));
        return adminService.extend(apiKey, days);
    }

    @PostMapping("/{apiKey}/rotate")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_MANAGE)
    public Object rotate(@PathVariable String apiKey, @RequestBody Map<String, Object> body) {
        Long graceHours = longOf(body.get("graceHours"));
        return adminService.rotate(apiKey, graceHours);
    }

    @DeleteMapping("/{apiKey}")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_MANAGE)
    public Object delete(@PathVariable String apiKey) {
        adminService.delete(apiKey);
        return Map.of("apiKey", apiKey, "deleted", true);
    }

    @GetMapping("/{apiKey}/stats/total")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_QUERY)
    public Object statsTotal(@PathVariable String apiKey) {
        return statsService.total(apiKey);
    }

    @GetMapping("/{apiKey}/stats/daily")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_QUERY)
    public Object statsDaily(@PathVariable String apiKey, @RequestParam(defaultValue = "7") int days) {
        return statsService.daily(apiKey, days);
    }

    @GetMapping("/{apiKey}/stats/last-used")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_QUERY)
    public Object statsLastUsed(@PathVariable String apiKey) {
        return statsService.lastUsed(apiKey);
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private static Integer integerOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(value.toString());
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringsOf(Object value) {
        return value instanceof List ? (List<String>) value : null;
    }
}
