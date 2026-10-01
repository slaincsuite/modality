package cn.slainc.modules.open.excel;

import cn.slainc.excel.ExcelUtils;
import cn.slainc.modules.open.service.OpenConsentAdminService;
import cn.slainc.openapi.model.App;
import cn.slainc.openapi.model.ConsentRecord;
import cn.slainc.openapi.spi.AppService;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ConsentExportService {

    private final OpenConsentAdminService consentAdminService;

    private final AppService appService;

    public ConsentExportService(OpenConsentAdminService consentAdminService, AppService appService) {
        this.consentAdminService = consentAdminService;
        this.appService = appService;
    }

    public void export(OutputStream outputStream, String userId, String appId, Boolean active, Long from, Long to,
                       int limit) {
        List<ConsentRecord> records = consentAdminService.exportRows(userId, appId, active, from, to, limit);
        Map<String, App> apps = appService.search(null, null, null).stream()
                .collect(Collectors.toMap(App::getAppId, Function.identity(), (left, right) -> left));
        List<ConsentExportRow> rows = new ArrayList<>();
        for (ConsentRecord record : records) {
            rows.add(new ConsentExportRow(record, apps.get(record.getAppId())));
        }
        ExcelUtils.write(outputStream, ConsentExportRow.class, rows, "授权记录");
    }
}
