package cn.slainc.excel.formula;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public final class FormulaWriter {

    private FormulaWriter() {
    }

    public static void write(Path path, FormulaSheet sheet) {
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            write(outputStream, sheet);
        } catch (IOException ex) {
            throw new IllegalStateException("导出公式工作簿失败: " + path, ex);
        }
    }

    public static void write(OutputStream outputStream, FormulaSheet sheet) {
        if (sheet == null) {
            throw new IllegalArgumentException("公式工作表不能为空");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet target = workbook.createSheet(sheet.sheetName() == null ? "Sheet1" : sheet.sheetName());
            CellStyle headerStyle = headerStyle(workbook);
            List<String> headers = sheet.headers();
            if (headers != null && !headers.isEmpty()) {
                Row headerRow = target.createRow(0);
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers.get(i));
                    cell.setCellStyle(headerStyle);
                }
            }
            int rowIndex = headers == null || headers.isEmpty() ? 0 : 1;
            List<Map.Entry<Cell, Object>> cached = new ArrayList<>();
            if (sheet.rows() != null) {
                for (List<Object> values : sheet.rows()) {
                    Row row = target.createRow(rowIndex++);
                    if (values == null) {
                        continue;
                    }
                    for (int i = 0; i < values.size(); i++) {
                        Object value = values.get(i);
                        Cell cell = row.createCell(i);
                        setValue(cell, value);
                        if (value instanceof Formula formula && formula.cachedValue() != null) {
                            cached.add(Map.entry(cell, formula.cachedValue()));
                        }
                    }
                }
            }
            evaluateAll(workbook);
            cached.forEach(entry -> applyCachedValue(entry.getKey(), entry.getValue()));
            workbook.write(outputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("导出公式工作簿失败", ex);
        }
    }

    private static void setValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Formula formula) {
            cell.setCellFormula(formula.expression());
            return;
        }
        if (value instanceof String text) {
            cell.setCellValue(text);
            return;
        }
        if (value instanceof Boolean flag) {
            cell.setCellValue(flag);
            return;
        }
        if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value instanceof LocalDate date) {
            cell.setCellValue(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            return;
        }
        if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant()));
            return;
        }
        if (value instanceof Date date) {
            cell.setCellValue(date);
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    private static void evaluateAll(Workbook workbook) {
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            Sheet sheet = workbook.getSheetAt(index);
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() != org.apache.poi.ss.usermodel.CellType.FORMULA) {
                        continue;
                    }
                    try {
                        evaluator.evaluateFormulaCell(cell);
                    } catch (RuntimeException ignored) {
                        // 无法计算的公式保留原样，交由读取方或 Excel 自行计算
                    }
                }
            }
        }
    }

    static void applyCachedValue(Cell cell, Object cachedValue) {
        if (!(cell instanceof XSSFCell xssfCell) || cachedValue == null) {
            return;
        }
        if (xssfCell.getCTCell().getF() == null) {
            return;
        }
        xssfCell.getCTCell().setV(cachedValue instanceof Number number
                ? BigDecimal.valueOf(number.doubleValue()).stripTrailingZeros().toPlainString()
                : String.valueOf(cachedValue));
    }

    static FormulaEvaluator evaluator(Workbook workbook) {
        return workbook.getCreationHelper().createFormulaEvaluator();
    }

    static Object valueOf(org.apache.poi.ss.usermodel.CellValue value) {
        if (value == null) {
            return null;
        }
        return switch (value.getCellType()) {
            case STRING -> value.getStringValue();
            case NUMERIC -> value.getNumberValue();
            case BOOLEAN -> value.getBooleanValue();
            default -> null;
        };
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}
