package cn.slainc.modules.upms.excel;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.excel.ExcelUtils;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.service.user.UserService;
import cn.slainc.utils.date.DateUtils;
import cn.slainc.utils.string.StringUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UserExcelService {

    private static final int MAX_IMPORT_ROWS = 5000;

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

    // 刻意不套事务：单行失败不影响其它行，整批回滚会让已成功的行凭空消失
    public Map<String, Object> importUsers(InputStream inputStream, Long tenantId, String operator) {
        List<UserImportRow> rows = ExcelUtils.read(inputStream, UserImportRow.class);
        if (rows.size() > MAX_IMPORT_ROWS) {
            throw new BizException("导入行数 " + rows.size() + " 超过上限 " + MAX_IMPORT_ROWS + "，请拆分文件");
        }
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
