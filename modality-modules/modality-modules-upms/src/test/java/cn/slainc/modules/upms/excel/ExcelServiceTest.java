package cn.slainc.modules.upms.excel;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.service.UserService;
import cn.idev.excel.EasyExcel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelServiceTest extends UpmsIntegrationTestBase {

    @Autowired
    private UserExcelService userExcelService;

    @Autowired
    private PermissionExcelService permissionExcelService;

    @Autowired
    private UserService userService;

    @Test
    void exportMasksPhoneAndEmail() {
        userService.create("excel-user", "表格用户", null, "someone@example.com", "13812348888", null,
                LoginType.TENANT_USER, 1L, "admin");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        userExcelService.export(out, userService.listAll());
        List<UserExportRow> rows = EasyExcel.read(new ByteArrayInputStream(out.toByteArray()))
                .head(UserExportRow.class).sheet().doReadSync();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getPhone()).isEqualTo("138****8888");
        assertThat(rows.get(0).getEmail()).isEqualTo("s******@example.com");
        assertThat(rows.get(0).getUsername()).isEqualTo("excel-user");
    }

    @Test
    void importCreatesUsersRowByRow() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, UserImportRow.class).sheet("用户导入").doWrite(List.of(
                rowOf("import-a", "TENANT_USER", "Import#12345"),
                rowOf("import-b", "TENANT_USER", null),
                rowOf("", "TENANT_USER", null)));
        Map<String, Object> report = userExcelService.importUsers(new ByteArrayInputStream(out.toByteArray()), 1L,
                "admin");
        assertThat(report.get("total")).isEqualTo(3);
        assertThat(report.get("success")).isEqualTo(2);
        assertThat(report.get("failure")).isEqualTo(1);
        assertThat(userService.findByUsername("import-a", LoginType.TENANT_USER)).isNotNull();
        assertThat(userService.get(userService.findByUsername("import-b", LoginType.TENANT_USER).getId())
                .getMustChangePassword()).isTrue();
    }

    @Test
    void duplicateImportRowsReportedPerRow() {
        userService.create("dup-user", null, null, null, null, null, LoginType.TENANT_USER, 1L, "admin");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, UserImportRow.class).sheet().doWrite(List.of(rowOf("dup-user", "TENANT_USER", null)));
        Map<String, Object> report = userExcelService.importUsers(new ByteArrayInputStream(out.toByteArray()), 1L,
                "admin");
        assertThat(report.get("failure")).isEqualTo(1);
        List<Map<String, Object>> failures = (List<Map<String, Object>>) report.get("failures");
        assertThat(failures.get(0).get("reason").toString()).contains("已存在");
    }

    @Test
    void importRejectsFileAboveRowLimit() {
        List<UserImportRow> rows = new ArrayList<>();
        for (int i = 0; i <= 5000; i++) {
            rows.add(rowOf("bulk-" + i, "TENANT_USER", null));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, UserImportRow.class).sheet().doWrite(rows);
        assertThatThrownBy(() -> userExcelService.importUsers(new ByteArrayInputStream(out.toByteArray()), 1L, "admin"))
                .isInstanceOf(BizException.class).hasMessageContaining("超过上限");
        assertThat(userService.findByUsername("bulk-0", LoginType.TENANT_USER)).isNull();
    }

    @Test
    void templateContainsOnlyHeaders() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        userExcelService.template(out);
        List<UserImportRow> rows = EasyExcel.read(new ByteArrayInputStream(out.toByteArray()))
                .head(UserImportRow.class).sheet().doReadSync();
        assertThat(rows).isEmpty();
    }

    @Test
    void permissionExportAndImportRoundTrip() {
        permissionExcelService.importPermissions(new ByteArrayInputStream(buildPermissionBook()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        permissionExcelService.export(out);
        List<PermissionExcelService.PermissionExportRow> rows = EasyExcel.read(
                        new ByteArrayInputStream(out.toByteArray()))
                .head(PermissionExcelService.PermissionExportRow.class).sheet().doReadSync();
        assertThat(rows).extracting(PermissionExcelService.PermissionExportRow::getCode)
                .containsExactly("perm:round:trip");
    }

    private UserImportRow rowOf(String username, String loginType, String password) {
        UserImportRow row = new UserImportRow();
        row.setUsername(username);
        row.setLoginType(loginType);
        row.setPassword(password);
        return row;
    }

    private byte[] buildPermissionBook() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PermissionExcelService.PermissionExportRow row =
                new PermissionExcelService.PermissionExportRow(null, "perm:round:trip", "往返权限", "API", null,
                        "enabled");
        EasyExcel.write(out, PermissionExcelService.PermissionExportRow.class).sheet("权限").doWrite(List.of(row));
        return out.toByteArray();
    }
}
