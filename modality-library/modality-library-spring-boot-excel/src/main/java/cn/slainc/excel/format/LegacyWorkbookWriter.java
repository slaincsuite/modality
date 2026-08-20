package cn.slainc.excel.format;

import cn.slainc.excel.write.SheetOptions;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.WorkbookUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class LegacyWorkbookWriter {

    private static final int MIN_COLUMN_WIDTH = 12;

    private LegacyWorkbookWriter() {
    }

    public static <T> void write(Path path, Class<T> head, List<T> data, String sheetName) {
        write(path, head, data, sheetName, null);
    }

    public static <T> void write(Path path, Class<T> head, List<T> data, String sheetName, SheetOptions options) {
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            write(outputStream, head, data, sheetName, options);
        } catch (IOException ex) {
            throw new IllegalStateException("导出 .xls 失败: " + path, ex);
        }
    }

    public static <T> void write(OutputStream outputStream, Class<T> head, List<T> data, String sheetName) {
        write(outputStream, head, data, sheetName, null);
    }

    public static <T> void write(OutputStream outputStream, Class<T> head, List<T> data, String sheetName,
                                 SheetOptions options) {
        BeanHead<T> beanHead = BeanHead.of(head);
        try (Workbook workbook = new HSSFWorkbook()) {
            String safeName = WorkbookUtil.createSafeSheetName(sheetName == null ? "Sheet1" : sheetName);
            Sheet sheet = workbook.createSheet(safeName);
            List<String> headers = beanHead.headers();
            CellStyle headerStyle = headerStyle(workbook);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, Math.max(MIN_COLUMN_WIDTH, headers.get(i).length() * 2 + 4) * 256);
            }
            if (data != null) {
                int rowIndex = 1;
                for (T bean : data) {
                    Row row = sheet.createRow(rowIndex++);
                    List<String> values = beanHead.valuesOf(bean);
                    for (int i = 0; i < values.size(); i++) {
                        row.createCell(i).setCellValue(values.get(i));
                    }
                }
            }
            applyOptions(sheet, options);
            workbook.write(outputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("导出 .xls 失败", ex);
        }
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static void applyOptions(Sheet sheet, SheetOptions options) {
        if (options == null || options.isEmpty()) {
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
            DataValidation validation = helper.createValidation(constraint,
                    new CellRangeAddressList(1, 10_000, column, column));
            validation.setSuppressDropDownArrow(true);
            validation.setShowErrorBox(true);
            sheet.addValidationData(validation);
        }
    }
}
