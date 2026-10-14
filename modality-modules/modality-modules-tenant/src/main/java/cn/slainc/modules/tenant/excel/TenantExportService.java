package cn.slainc.modules.tenant.excel;

import cn.slainc.excel.ExcelUtils;
import cn.slainc.modules.tenant.service.TenantManageService;
import cn.slainc.tenant.entity.Tenant;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class TenantExportService {

    private final TenantManageService tenantManageService;

    public TenantExportService(TenantManageService tenantManageService) {
        this.tenantManageService = tenantManageService;
    }

    public void export(OutputStream outputStream, String status, String keyword) {
        List<TenantExportRow> rows = new ArrayList<>();
        for (Tenant tenant : tenantManageService.listForExport(status, keyword)) {
            rows.add(new TenantExportRow(tenant));
        }
        ExcelUtils.write(outputStream, TenantExportRow.class, rows, "租户");
    }
}
