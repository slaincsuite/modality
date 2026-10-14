package cn.slainc.modules.infra.web.config;

import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.Result;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.modules.infra.service.config.DictService;
import cn.slainc.modules.infra.excel.DictTransferService;
import cn.slainc.security.annotation.RequirePermission;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "数据字典", description = "字典项维护")
@RestController
@RequestMapping("/infra/dict")
public class DictController {

    private final DictService dictService;

    private final DictTransferService transferService;

    public DictController(DictService dictService, DictTransferService transferService) {
        this.dictService = dictService;
        this.transferService = transferService;
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_DICT_QUERY)
    @GetMapping("/types/page")
    public Result<PageResult<DictService.DictTypeView>> typePage(@RequestParam(defaultValue = "1") long current,
                                                                  @RequestParam(defaultValue = "10") long size,
                                                                  @RequestParam(required = false) String keyword) {
        return Result.ok(dictService.typePage(current, size, keyword));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_DICT_CREATE)
    @PostMapping("/types")
    public Result<Void> createType(@RequestBody Map<String, Object> body) {
        dictService.createType(text(body.get("code")), text(body.get("name")), text(body.get("remark")));
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_DICT_UPDATE)
    @PutMapping("/types/{code}")
    public Result<Void> updateType(@PathVariable String code, @RequestBody Map<String, Object> body) {
        dictService.updateType(code, text(body.get("name")), text(body.get("remark")),
                body.containsKey("enabled") ? Boolean.parseBoolean(text(body.get("enabled"))) : null);
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_DICT_DELETE)
    @DeleteMapping("/types/{code}")
    public Result<Void> deleteType(@PathVariable String code) {
        dictService.deleteType(code);
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_DICT_QUERY)
    @GetMapping("/items/page")
    public Result<PageResult<DictService.DictItemView>> itemPage(@RequestParam(defaultValue = "1") long current,
                                                                  @RequestParam(defaultValue = "10") long size,
                                                                  @RequestParam(required = false) String typeCode,
                                                                  @RequestParam(required = false) String keyword) {
        return Result.ok(dictService.itemPage(current, size, typeCode, keyword));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_DICT_QUERY)
    @GetMapping("/items/by-type")
    public Result<List<DictService.DictItemView>> itemsByType(@RequestParam String typeCode) {
        return Result.ok(dictService.itemsByType(typeCode));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_DICT_QUERY)
    @GetMapping("/resolve")
    public Result<String> resolve(@RequestParam String typeCode, @RequestParam String value) {
        return Result.ok(dictService.resolveLabel(typeCode, value));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_DICT_CREATE)
    @PostMapping("/items")
    public Result<Void> createItem(@RequestBody Map<String, Object> body) {
        dictService.createItem(text(body.get("typeCode")), text(body.get("label")), text(body.get("value")),
                body.get("sort") == null ? null : Integer.parseInt(text(body.get("sort"))),
                text(body.get("remark")));
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_DICT_UPDATE)
    @PutMapping("/items/{id}")
    public Result<Void> updateItem(@PathVariable long id, @RequestBody Map<String, Object> body) {
        dictService.updateItem(id, text(body.get("label")), text(body.get("value")),
                body.get("sort") == null ? null : Integer.parseInt(text(body.get("sort"))),
                body.containsKey("enabled") ? Boolean.parseBoolean(text(body.get("enabled"))) : null,
                text(body.get("remark")));
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_DICT_DELETE)
    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(@PathVariable long id) {
        dictService.deleteItem(id);
        return Result.ok();
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_DICT_TRANSFER)
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String typeCode, HttpServletResponse response)
            throws IOException {
        transferService.export(response, typeCode);
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_DICT_TRANSFER)
    @PostMapping("/import")
    public Result<DictTransferService.ImportSummary> importItems(@RequestParam("file") MultipartFile file)
            throws IOException {
        return Result.ok(transferService.importItems(file.getInputStream()));
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
