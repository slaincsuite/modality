package cn.slainc.modules.open.excel;

import cn.slainc.excel.ExcelUtils;
import cn.slainc.modules.open.service.app.OpenAppAdminService;
import cn.slainc.openapi.domain.App;

import java.io.OutputStream;
import java.util.List;

public class AppExportService {

    private final OpenAppAdminService adminService;

    public AppExportService(OpenAppAdminService adminService) {
        this.adminService = adminService;
    }

    public void export(OutputStream outputStream, String keyword, String category, String appType, String status,
                       String ownerId) {
        List<App> apps = adminService.exportRows(keyword, category, appType, status, ownerId);
        List<AppExportRow> rows = apps.stream().map(AppExportRow::new).toList();
        ExcelUtils.write(outputStream, AppExportRow.class, rows, "应用清单");
    }
}
