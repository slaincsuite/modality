package cn.slainc.excel.format;

import cn.slainc.utils.date.DateUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LegacyWorkbookReader {

    private LegacyWorkbookReader() {
    }

    public static <T> List<T> read(Path path, Class<T> head) {
        return read(path, head, 0);
    }

    public static <T> List<T> read(Path path, Class<T> head, int sheetNo) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return read(inputStream, head, sheetNo);
        } catch (IOException ex) {
            throw new IllegalStateException("读取 .xls 失败: " + path, ex);
        }
    }

    public static <T> List<T> read(InputStream inputStream, Class<T> head) {
        return read(inputStream, head, 0);
    }

    public static <T> List<T> read(InputStream inputStream, Class<T> head, int sheetNo) {
        BeanHead<T> beanHead = BeanHead.of(head);
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (sheetNo < 0 || sheetNo >= workbook.getNumberOfSheets()) {
                throw new IllegalArgumentException("工作表下标越界: " + sheetNo);
            }
            Sheet sheet = workbook.getSheetAt(sheetNo);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<T> rows = new ArrayList<>();
            int firstRow = sheet.getFirstRowNum();
            Row headerRow = sheet.getRow(firstRow);
            List<String> headers = textRow(headerRow, evaluator, 0);
            for (int index = firstRow + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) {
                    continue;
                }
                List<String> values = textRow(row, evaluator, headers.size());
                if (values.stream().allMatch(value -> value == null || value.isBlank())) {
                    continue;
                }
                rows.add(beanHead.toBean(headers, values));
            }
            return rows;
        } catch (IOException ex) {
            throw new IllegalStateException("读取 .xls 失败", ex);
        }
    }

    private static List<String> textRow(Row row, FormulaEvaluator evaluator, int minSize) {
        if (row == null) {
            return new ArrayList<>();
        }
        int size = Math.max(row.getLastCellNum(), minSize);
        List<String> values = new ArrayList<>(size);
        for (int column = 0; column < size; column++) {
            values.add(cellText(row.getCell(column), evaluator));
        }
        return values;
    }

    private static String cellText(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            CellValue value = evaluator.evaluate(cell);
            return value == null ? "" : cellValueText(value);
        }
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> numericText(cell);
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private static String cellValueText(CellValue value) {
        return switch (value.getCellType()) {
            case STRING -> value.getStringValue();
            case NUMERIC -> BigDecimal.valueOf(value.getNumberValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(value.getBooleanValue());
            default -> "";
        };
    }

    private static String numericText(Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            return DateUtils.format(cell.getLocalDateTimeCellValue());
        }
        return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
    }
}
