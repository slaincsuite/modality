package cn.slainc.modules.infra.excel;

import cn.slainc.core.lang.BizAssert;
import cn.slainc.excel.ExcelUtils;
import cn.slainc.modules.infra.service.config.DictService;
import cn.slainc.web.http.DownloadResponses;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DictTransferService {

    public record ImportSummary(int succeeded, int failed, List<String> errors) {
    }

    private final DictService dictService;

    public DictTransferService(DictService dictService) {
        this.dictService = dictService;
    }

    public void export(HttpServletResponse response, String typeCode) throws IOException {
        List<DictService.DictItemView> items = dictService.itemsAll(typeCode);
        List<DictItemRow> rows = new ArrayList<>();
        for (DictService.DictItemView item : items) {
            DictItemRow row = new DictItemRow();
            row.setTypeCode(item.typeCode());
            row.setLabel(item.label());
            row.setValue(item.value());
            row.setSort(item.sort());
            row.setEnabled(item.enabled() ? "是" : "否");
            rows.add(row);
        }
        ExcelUtils.write(DownloadResponses.attachmentStream(response, "dict-items.xlsx",
                DownloadResponses.CONTENT_TYPE_EXCEL), DictItemRow.class, rows, "字典项");
    }

    public ImportSummary importItems(InputStream input) throws IOException {
        List<DictItemRow> rows = ExcelUtils.read(input, DictItemRow.class);
        BizAssert.notEmpty(rows, "导入文件没有可识别的字典项数据");
        int succeeded = 0;
        List<String> errors = new ArrayList<>();
        for (DictItemRow row : rows) {
            try {
                BizAssert.hasText(row.getTypeCode(), "字典编码不能为空");
                BizAssert.hasText(row.getLabel(), "字典标签不能为空");
                BizAssert.hasText(row.getValue(), "字典值不能为空");
                DictService.DictItemView existing = dictService.findItemByValue(row.getTypeCode(), row.getValue());
                if (existing == null) {
                    dictService.createItem(row.getTypeCode(), row.getLabel(), row.getValue(), row.getSort(), null);
                } else {
                    dictService.updateItem(existing.id(), row.getLabel(), null, row.getSort(),
                            !"否".equals(row.getEnabled()), null);
                }
                succeeded++;
            } catch (Exception exception) {
                errors.add(row.getTypeCode() + "/" + row.getValue() + "：" + exception.getMessage());
            }
        }
        return new ImportSummary(succeeded, errors.size(), errors);
    }
}
