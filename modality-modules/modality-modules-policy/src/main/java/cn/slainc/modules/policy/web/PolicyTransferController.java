package cn.slainc.modules.policy.web;

import cn.slainc.core.context.UserContext;
import cn.slainc.logger.audit.AuditCategory;
import cn.slainc.logger.audit.AuditLog;
import cn.slainc.logger.audit.AuditOperation;
import cn.slainc.modules.policy.PolicyCenterKeys;
import cn.slainc.modules.policy.service.PolicyTransferService;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.web.http.DownloadResponses;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/policy/transfer")
public class PolicyTransferController {

    private final PolicyTransferService transferService;

    public PolicyTransferController(PolicyTransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping(value = "/export/json", produces = MediaType.APPLICATION_JSON_VALUE)
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_TRANSFER)
    public void exportJson(@RequestParam(required = false) List<String> ids,
                           HttpServletResponse response) {
        DownloadResponses.writeJson(response, transferService.exportJson(ids, UserContext.username())
                .getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/import/json")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_TRANSFER)
    @AuditLog(value = "JSON 导入策略", operation = AuditOperation.IMPORT, category = AuditCategory.DATA,
            module = "policy", recordParams = false)
    public Map<String, Object> importJson(@RequestBody String json) {
        return transferService.importJson(json, UserContext.username());
    }

    @GetMapping("/export/excel")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_TRANSFER)
    public void exportExcel(@RequestParam(required = false) List<String> ids,
                            HttpServletResponse response) {
        transferService.exportExcel(ids, DownloadResponses.attachmentStream(response, "policies.xlsx",
                DownloadResponses.CONTENT_TYPE_EXCEL));
    }

    @PostMapping("/import/excel")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_TRANSFER)
    @AuditLog(value = "Excel 导入策略", operation = AuditOperation.IMPORT, category = AuditCategory.DATA,
            module = "policy", recordParams = false)
    public Map<String, Object> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        return transferService.importExcel(file, UserContext.username());
    }
}
