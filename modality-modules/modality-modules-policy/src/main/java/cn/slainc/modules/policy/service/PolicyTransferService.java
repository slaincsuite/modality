package cn.slainc.modules.policy.service;

import cn.slainc.excel.ExcelUtils;
import cn.slainc.modules.policy.dto.PolicyCreateRequest;
import cn.slainc.modules.policy.dto.PolicyView;
import cn.slainc.modules.policy.excel.PolicyExportRow;
import cn.slainc.utils.json.JsonUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PolicyTransferService {

    private final PolicyManageService manageService;

    public PolicyTransferService(PolicyManageService manageService) {
        this.manageService = manageService;
    }

    public String exportJson(List<String> ids, String operator) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (PolicyView view : targets(ids)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", view.name());
            item.put("subjectType", view.subjectType());
            item.put("subjectId", view.subjectId());
            item.put("loginType", view.loginType());
            item.put("description", view.description());
            item.put("group", view.group());
            item.put("document", JsonUtils.parse(view.document(), Object.class));
            items.add(item);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", items);
        payload.put("exportedBy", operator);
        payload.put("exportedAt", System.currentTimeMillis());
        return JsonUtils.toPrettyJson(payload);
    }

    public Map<String, Object> importJson(String json, String operator) {
        List<Map<String, Object>> items = readItems(json);
        return createAll(items.stream()
                .map(item -> new PolicyCreateRequest(text(item.get("name")), text(item.get("subjectType")),
                        text(item.get("subjectId")), text(item.get("loginType")), text(item.get("description")),
                        text(item.get("group")), JsonUtils.toJson(item.get("document"))))
                .toList(), operator);
    }

    public void exportExcel(List<String> ids, OutputStream outputStream) {
        List<PolicyExportRow> rows = new ArrayList<>();
        for (PolicyView view : targets(ids)) {
            rows.add(new PolicyExportRow(view));
        }
        ExcelUtils.write(outputStream, PolicyExportRow.class, rows, "策略");
    }

    public Map<String, Object> importExcel(MultipartFile file, String operator) throws IOException {
        List<PolicyExportRow> rows;
        try (InputStream inputStream = file.getInputStream()) {
            rows = ExcelUtils.read(inputStream, PolicyExportRow.class);
        }
        return createAll(rows.stream()
                .map(row -> new PolicyCreateRequest(row.getName(), row.getSubjectType(), row.getSubjectId(),
                        row.getLoginType(), row.getDescription(), row.getGroup(), row.getDocument()))
                .toList(), operator);
    }

    private Map<String, Object> createAll(List<PolicyCreateRequest> requests, String operator) {
        List<PolicyView> created = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (PolicyCreateRequest request : requests) {
            if (request.name() == null || request.name().isBlank() || request.documentJson() == null) {
                errors.add("跳过一条缺少名称或策略文档的记录");
                continue;
            }
            try {
                created.add(manageService.create(request, operator));
            } catch (RuntimeException e) {
                errors.add(request.name() + ": " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", requests.size());
        result.put("succeeded", created.size());
        result.put("failed", errors.size());
        result.put("errors", errors);
        return result;
    }

    private List<PolicyView> targets(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return manageService.page(null, null, null, null, null, null, 1, Integer.MAX_VALUE).records();
        }
        return ids.stream().map(manageService::view).toList();
    }

    private List<Map<String, Object>> readItems(String json) {
        Map<String, Object> payload = JsonUtils.parse(json, Map.class);
        Object items = payload == null ? null : payload.get("items");
        if (items == null && JsonUtils.isValid(json) && json.trim().startsWith("[")) {
            items = JsonUtils.parseList(json, Map.class);
        }
        if (!(items instanceof List<?> list)) {
            throw new IllegalArgumentException("导入内容必须是 {\"items\":[...]} 或策略数组 JSON");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
