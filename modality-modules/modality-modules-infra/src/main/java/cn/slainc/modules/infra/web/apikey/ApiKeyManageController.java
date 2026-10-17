package cn.slainc.modules.infra.web.apikey;

import cn.dev33.satoken.apikey.model.ApiKeyModel;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.apikey.support.ApiKeyAdminService;
import cn.slainc.apikey.support.ApiKeyStatsService;
import cn.slainc.modules.infra.context.CurrentUser;
import cn.slainc.modules.infra.service.apikey.ApiKeyManageService;
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

    private final ApiKeyManageService manageService;

    private final CurrentUser currentUser;

    public ApiKeyManageController(ApiKeyAdminService adminService, ApiKeyStatsService statsService,
                                  ApiKeyManageService manageService, CurrentUser currentUser) {
        this.adminService = adminService;
        this.statsService = statsService;
        this.manageService = manageService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_QUERY)
    public Result<List<ApiKeyManageService.ApiKeyView>> list(@RequestParam String loginId) {
        return Result.ok(manageService.listByLoginId(loginId));
    }

    @GetMapping("/page")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_QUERY)
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(defaultValue = "1") long current,
                                                        @RequestParam(defaultValue = "10") long size) {
        return Result.ok(manageService.page(status, keyword, current, size));
    }

    @GetMapping(value = "/{loginId}/export.csv", produces = "text/csv;charset=UTF-8")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_QUERY)
    public String exportCsv(@PathVariable String loginId) {
        return adminService.exportCsv(loginId);
    }

    @PostMapping("/issue")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_ISSUE)
    @AuditLog(value = "签发 API Key", operation = AuditOperation.CREATE, category = AuditCategory.SECURITY,
            module = "infra", recordParams = false)
    public Result<Object> issue(@RequestBody Map<String, Object> body) {
        String loginId = stringOf(body.get("loginId"));
        String title = stringOf(body.get("title"));
        List<String> scopes = stringsOf(body.get("scopes"));
        Long ttlDays = longOf(body.get("ttlDays"));
        @SuppressWarnings("unchecked")
        Map<String, Object> extraData = (Map<String, Object>) body.get("extraData");
        Integer count = integerOf(body.get("count"));
        List<ApiKeyManageService.IssuedKeyView> issued = manageService.issue(loginId, title,
                firstText(body.get("intro"), body.get("description")), scopes, ttlDays, extraData,
                count == null ? 1 : count);
        return Result.ok(issued.size() == 1 ? issued.get(0) : issued);
    }

    @PostMapping("/{apiKey}/enable")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_MANAGE)
    @AuditLog(value = "启用 API Key", operation = AuditOperation.UPDATE, category = AuditCategory.SECURITY,
            module = "infra", recordParams = false)
    public Result<Map<String, String>> enable(@PathVariable String apiKey) {
        adminService.enable(apiKey);
        return Result.ok(Map.of("apiKey", apiKey, "status", "ENABLED"));
    }

    @PostMapping("/{apiKey}/disable")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_MANAGE)
    @AuditLog(value = "禁用 API Key", operation = AuditOperation.UPDATE, category = AuditCategory.SECURITY,
            module = "infra", recordParams = false)
    public Result<Map<String, String>> disable(@PathVariable String apiKey) {
        adminService.disable(apiKey);
        return Result.ok(Map.of("apiKey", apiKey, "status", "DISABLED"));
    }

    @PostMapping("/{apiKey}/extend")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_MANAGE)
    @AuditLog(value = "延期 API Key", operation = AuditOperation.UPDATE, category = AuditCategory.SECURITY,
            module = "infra", recordParams = false)
    public Result<ApiKeyModel> extend(@PathVariable String apiKey, @RequestBody Map<String, Object> body) {
        Long days = longOf(body.get("days"));
        return Result.ok(adminService.extend(apiKey, days));
    }

    @PostMapping("/{apiKey}/rotate")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_MANAGE)
    @AuditLog(value = "轮换 API Key", operation = AuditOperation.UPDATE, category = AuditCategory.SECURITY,
            module = "infra", recordParams = false)
    public Result<ApiKeyManageService.IssuedKeyView> rotate(@PathVariable String apiKey,
                                                            @RequestBody Map<String, Object> body) {
        Long graceHours = longOf(body.get("graceHours"));
        return Result.ok(manageService.rotate(apiKey, graceHours == null ? 0L : graceHours, currentUser.operator()));
    }

    @DeleteMapping("/{apiKey}")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_MANAGE)
    @AuditLog(value = "删除 API Key", operation = AuditOperation.DELETE, category = AuditCategory.SECURITY,
            module = "infra", recordParams = false)
    public Result<Map<String, Object>> delete(@PathVariable String apiKey) {
        adminService.delete(apiKey);
        return Result.ok(Map.of("apiKey", apiKey, "deleted", true));
    }

    @GetMapping("/{apiKey}")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_QUERY)
    public Result<ApiKeyManageService.ApiKeyView> detail(@PathVariable String apiKey) {
        return Result.ok(manageService.detail(apiKey));
    }

    @GetMapping("/{apiKey}/rotations")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_QUERY)
    public Result<List<ApiKeyManageService.RotationView>> rotations(@PathVariable String apiKey) {
        return Result.ok(manageService.rotations(apiKey));
    }

    @GetMapping("/{apiKey}/stats")
    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_QUERY)
    public Result<ApiKeyManageService.ApiKeyStatsView> stats(@PathVariable String apiKey) {
        return Result.ok(manageService.stats(apiKey));
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

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = stringOf(value);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
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
