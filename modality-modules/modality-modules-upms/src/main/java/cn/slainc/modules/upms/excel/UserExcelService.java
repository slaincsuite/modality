package cn.slainc.modules.upms.excel;

import cn.slainc.core.enums.LoginType;
import cn.slainc.excel.ExcelUtils;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.service.UserService;
import cn.slainc.utils.date.DateUtils;
import cn.slainc.utils.string.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UserExcelService {

    private final UserService userService;

    public UserExcelService(UserService userService) {
        this.userService = userService;
    }

    public void export(OutputStream outputStream, List<UserRecord> users) {
        List<UserExportRow> rows = new ArrayList<>();
        for (UserRecord user : users) {
            rows.add(new UserExportRow(user.getId(), user.getUsername(), user.getNickname(), user.getLoginType(),
                    StringUtils.maskMobile(user.getPhone()), StringUtils.maskEmail(user.getEmail()),
                    user.getStatus(),
                    user.getCreateTime() == null ? ""
                            : DateUtils.format(DateUtils.fromEpochMilli(user.getCreateTime()))));
        }
        ExcelUtils.write(outputStream, UserExportRow.class, rows, "用户");
    }

    public void template(OutputStream outputStream) {
        ExcelUtils.write(outputStream, UserImportRow.class, List.of(), "用户导入");
    }

    // 逐行独立落库：单行失败不影响其它行，行级错误回传给管理后台展示
    @Transactional
    public Map<String, Object> importUsers(InputStream inputStream, Long tenantId, String operator) {
        List<UserImportRow> rows = ExcelUtils.read(inputStream, UserImportRow.class);
        List<Map<String, Object>> failures = new ArrayList<>();
        int success = 0;
        int rowNumber = 1;
        for (UserImportRow row : rows) {
            rowNumber++;
            try {
                LoginType type = row.getLoginType() == null || row.getLoginType().isBlank()
                        ? LoginType.TENANT_USER : LoginType.of(row.getLoginType());
                if (type == null) {
                    throw new IllegalArgumentException("非法的账号体系: " + row.getLoginType());
                }
                userService.create(row.getUsername(), row.getNickname(), row.getPassword(), row.getEmail(),
                        row.getPhone(), row.getDeptId(), type, tenantId, operator);
                success++;
            } catch (Exception e) {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("row", rowNumber);
                failure.put("username", row.getUsername());
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
}
