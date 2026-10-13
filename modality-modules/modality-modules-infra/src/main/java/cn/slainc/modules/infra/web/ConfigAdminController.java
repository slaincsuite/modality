package cn.slainc.modules.infra.web;

import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.Result;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.modules.infra.service.ConfigAdminService;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.utils.json.JsonUtils;
import cn.slainc.web.http.DownloadResponses;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "配置管理", description = "动态配置查看与修改")
@RestController
@RequestMapping("/infra/config")
public class ConfigAdminController {

    private final ConfigAdminService configAdminService;

    public ConfigAdminController(ConfigAdminService configAdminService) {
        this.configAdminService = configAdminService;
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_CONFIG_QUERY)
    @GetMapping("/page")
    public Result<PageResult<ConfigAdminService.ConfigView>> page(@RequestParam(defaultValue = "1") long current,
                                                                   @RequestParam(defaultValue = "10") long size,
                                                                   @RequestParam(required = false) String keyword,
                                                                   @RequestParam(required = false) String group) {
        return Result.ok(configAdminService.page(current, size, keyword, group));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_CONFIG_QUERY)
    @GetMapping("/groups")
    public Result<List<String>> groups() {
        return Result.ok(configAdminService.groups());
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_CONFIG_QUERY)
    @GetMapping("/{key}")
    public Result<ConfigAdminService.ConfigView> detail(@PathVariable String key) {
        return Result.ok(configAdminService.detail(key));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_CONFIG_CREATE)
    @PostMapping
    public Result<Void> create(@RequestBody Map<String, Object> body) {
        configAdminService.create(text(body.get("key")), text(body.get("value")), text(body.get("group")),
                text(body.get("description")), Boolean.parseBoolean(text(body.getOrDefault("encrypted", "false"))));
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_CONFIG_UPDATE)
    @PutMapping("/{key}")
    public Result<Void> update(@PathVariable String key, @RequestBody Map<String, Object> body) {
        configAdminService.update(key, body.containsKey("value") ? text(body.get("value")) : null,
                body.containsKey("group") ? text(body.get("group")) : null,
                body.containsKey("description") ? text(body.get("description")) : null);
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_CONFIG_DELETE)
    @DeleteMapping("/{key}")
    public Result<Void> delete(@PathVariable String key) {
        configAdminService.delete(key);
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_CONFIG_QUERY)
    @GetMapping("/history/page")
    public Result<PageResult<ConfigAdminService.HistoryView>> historyPage(
            @RequestParam String key, @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(configAdminService.historyPage(key, current, size));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_CONFIG_ROLLBACK)
    @PostMapping("/history/{id}/rollback")
    public Result<Void> rollback(@PathVariable long id) {
        configAdminService.rollback(id);
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_CONFIG_TRANSFER)
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String group, @RequestParam(required = false) String keyword,
                       HttpServletResponse response) {
        List<ConfigAdminService.ExportItem> items = configAdminService.exportItems(group, keyword);
        DownloadResponses.writeAttachment(response, "dynamic-config.json", DownloadResponses.CONTENT_TYPE_JSON,
                JsonUtils.toJson(Map.of("items", items)).getBytes(StandardCharsets.UTF_8));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_CONFIG_TRANSFER)
    @PostMapping("/import")
    public Result<ConfigAdminService.ImportSummary> importItems(@RequestBody Map<String, Object> body) {
        Object rawItems = body.get("items");
        cn.slainc.core.lang.BizAssert.isTrue(rawItems instanceof List<?>, "导入载荷缺少 items 数组");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) rawItems;
        return Result.ok(configAdminService.importItems(items));
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
