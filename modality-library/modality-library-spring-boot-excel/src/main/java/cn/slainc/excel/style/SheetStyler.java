package cn.slainc.excel.style;

import cn.slainc.excel.support.WorkbookAccess;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ComparisonOperator;
import org.apache.poi.ss.usermodel.ConditionalFormatting;
import org.apache.poi.ss.usermodel.ConditionalFormattingRule;
import org.apache.poi.ss.usermodel.FontFormatting;
import org.apache.poi.ss.usermodel.PatternFormatting;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

import java.nio.file.Path;
import java.util.List;

public final class SheetStyler {

    private SheetStyler() {
    }

    public static void applyCellStyle(Path file, int sheetIndex, int rowIndex, int columnIndex, StyleSpec spec) {
        WorkbookAccess.edit(file, workbook -> {
            Sheet sheet = sheetAt(workbook, sheetIndex);
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                throw new IllegalArgumentException("第 " + (rowIndex + 1) + " 行不存在");
            }
            Cell cell = row.getCell(columnIndex);
            if (cell == null) {
                throw new IllegalArgumentException("第 " + (rowIndex + 1) + " 行第 " + (columnIndex + 1) + " 列不存在");
            }
            cell.setCellStyle(StyleApplier.apply(workbook, spec));
        });
    }

    public static void applyRowStyle(Path file, int sheetIndex, int rowIndex, StyleSpec spec) {
        WorkbookAccess.edit(file, workbook -> {
            Sheet sheet = sheetAt(workbook, sheetIndex);
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                throw new IllegalArgumentException("第 " + (rowIndex + 1) + " 行不存在");
            }
            CellStyle style = StyleApplier.apply(workbook, spec);
            for (int column = row.getFirstCellNum(); column < row.getLastCellNum(); column++) {
                Cell cell = row.getCell(column);
                if (cell != null) {
                    cell.setCellStyle(style);
                }
            }
        });
    }

    public static void applyRowHeight(Path file, int sheetIndex, int rowIndex, float heightInPoints) {
        if (heightInPoints <= 0) {
            throw new IllegalArgumentException("行高必须大于 0");
        }
        WorkbookAccess.edit(file, workbook -> {
            Sheet sheet = sheetAt(workbook, sheetIndex);
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                throw new IllegalArgumentException("第 " + (rowIndex + 1) + " 行不存在");
            }
            row.setHeightInPoints(heightInPoints);
        });
    }

    public static void addConditionalFormat(Path file, int sheetIndex, ConditionalFormatSpec spec) {
        WorkbookAccess.edit(file, workbook -> {
            Sheet sheet = sheetAt(workbook, sheetIndex);
            SheetConditionalFormatting formatting = sheet.getSheetConditionalFormatting();
            ConditionalFormattingRule rule = rule(formatting, spec);
            PatternFormatting pattern = rule.createPatternFormatting();
            if (spec.getFillColor() != null) {
                applyPatternColor(workbook, pattern, spec.getFillColor());
            }
            FontFormatting font = rule.createFontFormatting();
            if (spec.getFontColor() != null) {
                applyFontColor(workbook, font, spec.getFontColor());
            }
            if (spec.isBold()) {
                font.setFontStyle(true, false);
            }
            formatting.addConditionalFormatting(new CellRangeAddress[]{CellRangeAddress.valueOf(spec.getRange())},
                    rule);
        });
    }

    public static int conditionalFormatCount(Path file, int sheetIndex) {
        try (Workbook workbook = WorkbookAccess.open(file)) {
            return sheetAt(workbook, sheetIndex).getSheetConditionalFormatting().getNumConditionalFormattings();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("读取条件格式失败: " + file, ex);
        }
    }

    public static List<String> conditionalFormatRanges(Path file, int sheetIndex) {
        try (Workbook workbook = WorkbookAccess.open(file)) {
            SheetConditionalFormatting formatting = sheetAt(workbook, sheetIndex).getSheetConditionalFormatting();
            List<String> ranges = new java.util.ArrayList<>();
            for (int index = 0; index < formatting.getNumConditionalFormattings(); index++) {
                ConditionalFormatting conditionalFormatting = formatting.getConditionalFormattingAt(index);
                for (CellRangeAddress range : conditionalFormatting.getFormattingRanges()) {
                    ranges.add(range.formatAsString());
                }
            }
            return ranges;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("读取条件格式失败: " + file, ex);
        }
    }

    private static Sheet sheetAt(Workbook workbook, int sheetIndex) {
        if (sheetIndex < 0 || sheetIndex >= workbook.getNumberOfSheets()) {
            throw new IllegalArgumentException("工作表下标越界: " + sheetIndex);
        }
        return workbook.getSheetAt(sheetIndex);
    }

    private static ConditionalFormattingRule rule(SheetConditionalFormatting formatting, ConditionalFormatSpec spec) {
        String value1 = spec.getValue1();
        return switch (spec.getOperator()) {
            case GREATER_THAN -> formatting.createConditionalFormattingRule(ComparisonOperator.GT, value1);
            case LESS_THAN -> formatting.createConditionalFormattingRule(ComparisonOperator.LT, value1);
            case EQUAL -> formatting.createConditionalFormattingRule(ComparisonOperator.EQUAL, value1);
            case NOT_EQUAL -> formatting.createConditionalFormattingRule(ComparisonOperator.NOT_EQUAL, value1);
            case BETWEEN -> formatting.createConditionalFormattingRule(ComparisonOperator.BETWEEN, value1,
                    spec.getValue2());
        };
    }

    private static void applyPatternColor(Workbook workbook, PatternFormatting pattern, String color) {
        if (workbook instanceof HSSFWorkbook) {
            pattern.setFillForegroundColor(StyleApplier.hssfIndex(workbook, color));
        } else {
            pattern.setFillForegroundColor(StyleApplier.xssfColor(color));
        }
        pattern.setFillPattern(org.apache.poi.ss.usermodel.PatternFormatting.SOLID_FOREGROUND);
    }

    private static void applyFontColor(Workbook workbook, FontFormatting font, String color) {
        if (workbook instanceof HSSFWorkbook) {
            font.setFontColorIndex(StyleApplier.hssfIndex(workbook, color));
        } else {
            font.setFontColor(StyleApplier.xssfColor(color));
        }
    }
}
