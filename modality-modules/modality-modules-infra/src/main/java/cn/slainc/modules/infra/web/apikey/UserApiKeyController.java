package cn.slainc.modules.infra.web.apikey;

import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.modules.infra.service.apikey.ApiKeyManageService;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户 API 密钥", description = "按用户维度查询、签发与管理 API 密钥")
@RestController
@RequestMapping("/upms/user/{id}/api-keys")
public class UserApiKeyController {

    private final ApiKeyManageService apiKeyManageService;

    public UserApiKeyController(ApiKeyManageService apiKeyManageService) {
        this.apiKeyManageService = apiKeyManageService;
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_QUERY)
    @GetMapping
    public Result<List<ApiKeyManageService.ApiKeyView>> list(@PathVariable String id) {
        return Result.ok(apiKeyManageService.listByLoginId(id));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_ISSUE)
    @AuditLog(value = "为用户签发 API Key", operation = AuditOperation.CREATE, category = AuditCategory.SECURITY,
            module = "infra", recordParams = false)
    @PostMapping
    public Result<Object> issue(@PathVariable String id, @RequestBody Map<String, Object> body) {
        List<ApiKeyManageService.IssuedKeyView> issued = apiKeyManageService.issue(id, text(body.get("title")),
                firstText(body.get("intro"), body.get("description")), strings(body.get("scopes")),
                longOf(body.get("ttlDays")), extraData(body.get("extraData")), integerOf(body.get("count"), 1));
        return Result.ok(issued.size() == 1 ? issued.get(0) : issued);
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_APIKEY_MANAGE)
    @AuditLog(value = "管理用户 API Key", operation = AuditOperation.UPDATE, category = AuditCategory.SECURITY,
            module = "infra", recordParams = false)
    @PutMapping
    public Result<ApiKeyManageService.ApiKeyView> update(@PathVariable String id,
                                                         @RequestBody Map<String, Object> body) {
        Boolean enabled = booleanOf(body.get("enabled"));
        if (enabled == null && body.get("status") != null) {
            enabled = "enabled".equalsIgnoreCase(String.valueOf(body.get("status")));
        }
        return Result.ok(apiKeyManageService.updateForUser(id, text(body.get("apiKey")), text(body.get("title")),
                firstText(body.get("intro"), body.get("description")), enabled));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extraData(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object value) {
        return value instanceof List<?> list ? (List<String>) list : null;
    }

    private static Integer integerOf(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private static Long longOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private static Boolean booleanOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
