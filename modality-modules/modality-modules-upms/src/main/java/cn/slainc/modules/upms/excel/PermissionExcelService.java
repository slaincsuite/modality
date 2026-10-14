package cn.slainc.modules.upms.excel;

import cn.idev.excel.annotation.ExcelProperty;
import cn.slainc.excel.ExcelUtils;
import cn.slainc.modules.upms.entity.PermissionRecord;
import cn.slainc.modules.upms.service.permission.PermissionService;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PermissionExcelService {

    private final PermissionService permissionService;

    public PermissionExcelService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public void export(OutputStream outputStream) {
        List<PermissionExportRow> rows = new ArrayList<>();
        for (PermissionRecord permission : permissionService.search(null, null)) {
            rows.add(new PermissionExportRow(permission.getId(), permission.getCode(), permission.getName(),
                    permission.getType(), permission.getParentId(), permission.getStatus()));
        }
        ExcelUtils.write(outputStream, PermissionExportRow.class, rows, "权限");
    }

    public Map<String, Object> importPermissions(InputStream inputStream) {
        List<PermissionExportRow> rows = ExcelUtils.read(inputStream, PermissionExportRow.class);
        List<Map<String, Object>> failures = new ArrayList<>();
        int success = 0;
        int rowNumber = 1;
        for (PermissionExportRow row : rows) {
            rowNumber++;
            try {
                permissionService.create(row.getCode(), row.getName(), row.getType(), row.getParentId(), 0, null);
                success++;
            } catch (Exception e) {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("row", rowNumber);
                failure.put("code", row.getCode());
                failure.put("reason", e.getMessage());
                failures.add(failure);
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("total", rows.size());
        report.put("success", success);
        report.put("failure", failures.size());
        report.put("failures", failures);
        return report;
    }

    public static class PermissionExportRow {

        @ExcelProperty("权限ID")
        private Long permissionId;

        @ExcelProperty("编码")
        private String code;

        @ExcelProperty("名称")
        private String name;

        @ExcelProperty("类型")
        private String type;

        @ExcelProperty("上级ID")
        private Long parentId;

        @ExcelProperty("状态")
        private String status;

        public PermissionExportRow() {
        }

        public PermissionExportRow(Long permissionId, String code, String name, String type, Long parentId,
                                   String status) {
            this.permissionId = permissionId;
            this.code = code;
            this.name = name;
            this.type = type;
            this.parentId = parentId;
            this.status = status;
        }

        public Long getPermissionId() {
            return permissionId;
        }

        public void setPermissionId(Long permissionId) {
            this.permissionId = permissionId;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Long getParentId() {
            return parentId;
        }

        public void setParentId(Long parentId) {
            this.parentId = parentId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
