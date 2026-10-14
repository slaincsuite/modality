package cn.slainc.modules.tenant.excel;

import cn.slainc.excel.ExcelUtils;
import cn.slainc.tenant.entity.Tenant;
import cn.slainc.utils.string.StringUtils;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class TenantExcelService {

    public void export(OutputStream outputStream, List<Tenant> tenants) {
        List<TenantExportRow> rows = new ArrayList<>();
        for (Tenant tenant : tenants) {
            rows.add(new TenantExportRow(String.valueOf(tenant.getId()), tenant.getTenantCode(),
                    tenant.getTenantName(),
                    tenant.getStatus() == null ? "" : tenant.getStatus().name(),
                    tenant.getIsolationStrategy() == null ? "" : tenant.getIsolationStrategy().name(),
                    tenant.getExpireAt() == null ? "" : tenant.getExpireAt().toString(),
                    tenant.getContactName() == null ? "" : tenant.getContactName(),
                    StringUtils.maskMobile(tenant.getContactPhone()),
                    StringUtils.maskEmail(tenant.getContactEmail())));
        }
        ExcelUtils.write(outputStream, TenantExportRow.class, rows, "租户");
    }

    public record TenantExportRow(String id, String tenantCode, String tenantName, String status,
                                  String isolationStrategy, String expireAt, String contactName,
                                  String contactPhone, String contactEmail) {
    }
}
