package cn.slainc.modules.open.excel;

import cn.slainc.excel.ExcelUtils;
import cn.slainc.modules.open.service.consent.OpenConsentAdminService;
import cn.slainc.modules.open.dto.ConsentView;

import java.io.OutputStream;
import java.util.List;

public class ConsentExportService {

    private final OpenConsentAdminService consentAdminService;

    public ConsentExportService(OpenConsentAdminService consentAdminService) {
        this.consentAdminService = consentAdminService;
    }

    public void export(OutputStream outputStream, String userId, String appId, Boolean active, Long from, Long to,
                       int limit) {
        List<ConsentView> records = consentAdminService.exportRows(userId, appId, active, from, to, limit);
        List<ConsentExportRow> rows = records.stream().map(ConsentExportRow::new).toList();
        ExcelUtils.write(outputStream, ConsentExportRow.class, rows, "授权记录");
    }
}
