package cn.slainc.excel.write;

import cn.idev.excel.write.handler.SheetWriteHandler;
import cn.idev.excel.write.metadata.holder.WriteSheetHolder;
import cn.idev.excel.write.metadata.holder.WriteWorkbookHolder;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;

import java.util.List;
import java.util.Map;

public class SheetOptionHandler implements SheetWriteHandler {

    private final SheetOptions options;

    public SheetOptionHandler(SheetOptions options) {
        this.options = options == null ? SheetOptions.defaults() : options;
    }

    @Override
    public void afterSheetCreate(WriteWorkbookHolder workbookHolder, WriteSheetHolder sheetHolder) {
        if (options.isEmpty()) {
            return;
        }
        Sheet sheet = sheetHolder.getSheet();
        if (sheet == null) {
            return;
        }
        if (options.getFreezeRows() > 0 || options.getFreezeColumns() > 0) {
            sheet.createFreezePane(options.getFreezeColumns(), options.getFreezeRows());
        }
        for (Map.Entry<Integer, Integer> entry : options.getColumnWidths().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) {
                sheet.setColumnWidth(entry.getKey(), entry.getValue() * 256);
            }
        }
        if (options.isAutoFilter() && options.getAutoFilterColumnCount() > 0) {
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, options.getAutoFilterColumnCount() - 1));
        }
        applyDropdowns(sheet);
    }

    private void applyDropdowns(Sheet sheet) {
        if (options.getDropdowns().isEmpty()) {
            return;
        }
        DataValidationHelper helper = sheet.getDataValidationHelper();
        for (Map.Entry<Integer, List<String>> entry : options.getDropdowns().entrySet()) {
            Integer column = entry.getKey();
            List<String> values = entry.getValue();
            if (column == null || values == null || values.isEmpty()) {
                continue;
            }
            DataValidationConstraint constraint = helper.createExplicitListConstraint(values.toArray(new String[0]));
            CellRangeAddressList regions = new CellRangeAddressList(1, 10_000, column, column);
            DataValidation validation = helper.createValidation(constraint, regions);
            validation.setSuppressDropDownArrow(true);
            validation.setShowErrorBox(true);
            sheet.addValidationData(validation);
        }
    }
}
