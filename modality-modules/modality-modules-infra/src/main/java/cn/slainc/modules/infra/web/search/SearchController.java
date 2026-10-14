package cn.slainc.modules.infra.web.search;

import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.modules.infra.service.search.SearchDocumentService;
import cn.slainc.modules.infra.service.search.SearchManagementService;
import cn.slainc.search.spi.SearchHit;
import cn.slainc.search.spi.SearchStats;
import cn.slainc.search.spi.SearchDocumentPayload;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
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

@Tag(name = "搜索引擎", description = "内置倒排索引的检索与索引维护")
@RestController
@RequestMapping("/infra/search")
public class SearchController {

    private final SearchManagementService searchManagementService;

    public SearchController(SearchManagementService searchManagementService) {
        this.searchManagementService = searchManagementService;
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_SEARCH_QUERY)
    @GetMapping
    public Result<PageResult<SearchHit>> search(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) List<String> types,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        return Result.ok(searchManagementService.search(keyword, types, page, size));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_SEARCH_QUERY)
    @GetMapping("/documents")
    public Result<PageResult<SearchDocumentService.DocumentView>> documents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(searchManagementService.documents(keyword, sourceType, enabled, current, size));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_SEARCH_INDEX)
    @AuditLog(value = "写入检索文档", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "infra", recordParams = false)
    @PostMapping("/documents")
    public Result<SearchDocumentService.DocumentView> index(@RequestBody Map<String, Object> body) {
        SearchDocumentPayload payload = new SearchDocumentPayload(text(body.get("sourceType")),
                text(body.get("sourceId")), text(body.get("title")), text(body.get("summary")),
                text(body.get("content")), text(body.get("tags")), text(body.get("url")),
                body.get("enabled") == null ? null : Boolean.parseBoolean(text(body.get("enabled"))));
        return Result.ok(searchManagementService.index(payload));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_SEARCH_REMOVE)
    @AuditLog(value = "删除检索文档", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "infra")
    @DeleteMapping("/documents/{sourceType}/{sourceId}")
    public Result<Integer> remove(@PathVariable String sourceType, @PathVariable String sourceId) {
        return Result.ok(searchManagementService.remove(sourceType, sourceId));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_SEARCH_QUERY)
    @GetMapping("/stats")
    public Result<SearchStats> stats() {
        return Result.ok(searchManagementService.stats());
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_SEARCH_INDEX)
    @AuditLog(value = "重建检索索引", operation = AuditOperation.CONFIG, category = AuditCategory.DATA,
            module = "infra")
    @PostMapping("/reindex")
    public Result<Integer> reindex(@RequestBody(required = false) Map<String, Object> body) {
        return Result.ok(searchManagementService.reindex(types(body)));
    }

    private static List<String> types(Map<String, Object> body) {
        if (body == null || !(body.get("sourceTypes") instanceof List<?> raw)) {
            return List.of();
        }
        List<String> types = new ArrayList<>();
        for (Object item : raw) {
            if (item != null && !String.valueOf(item).isBlank()) {
                types.add(String.valueOf(item));
            }
        }
        return types;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
