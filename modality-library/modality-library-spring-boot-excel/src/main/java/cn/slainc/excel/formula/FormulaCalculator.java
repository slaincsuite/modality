package cn.slainc.excel.formula;

import cn.slainc.excel.ExcelUtils;
import cn.slainc.excel.support.WorkbookAccess;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FormulaCalculator {

    private FormulaCalculator() {
    }

    public static Map<String, Object> evaluate(Path file) {
        Map<String, Object> values = new LinkedHashMap<>();
        WorkbookAccess.edit(file, workbook -> {
            FormulaEvaluator evaluator = FormulaWriter.evaluator(workbook);
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        if (cell.getCellType() != CellType.FORMULA) {
                            continue;
                        }
                        CellValue value = evaluator.evaluate(cell);
                        values.put(sheet.getSheetName() + "!" + cell.getAddress().formatAsString(),
                                FormulaWriter.valueOf(value));
                    }
                }
            }
            evaluator.evaluateAll();
        });
        return values;
    }

    public static Object evaluateCell(Path file, String reference) {
        try (Workbook workbook = WorkbookAccess.open(file)) {
            FormulaEvaluator evaluator = FormulaWriter.evaluator(workbook);
            Sheet sheet = workbook.getSheetAt(0);
            CellReference cellReference = new CellReference(reference);
            Row row = sheet.getRow(cellReference.getRow());
            if (row == null) {
                throw new IllegalArgumentException("单元格 " + reference + " 不存在");
            }
            Cell cell = row.getCell(cellReference.getCol());
            if (cell == null) {
                throw new IllegalArgumentException("单元格 " + reference + " 不存在");
            }
            return FormulaWriter.valueOf(evaluator.evaluate(cell));
        } catch (IOException ex) {
            throw new IllegalStateException("计算公式失败: " + file, ex);
        }
    }

    public static String formulaOf(Path file, String reference) {
        try (Workbook workbook = WorkbookAccess.open(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            CellReference cellReference = new CellReference(reference);
            Row row = sheet.getRow(cellReference.getRow());
            Cell cell = row == null ? null : row.getCell(cellReference.getCol());
            if (cell == null || cell.getCellType() != CellType.FORMULA) {
                return null;
            }
            return "=" + cell.getCellFormula();
        } catch (IOException ex) {
            throw new IllegalStateException("读取公式失败: " + file, ex);
        }
    }

    public static List<String> formulas(Path file) {
        List<String> formulas = new ArrayList<>();
        try (Workbook workbook = WorkbookAccess.open(file)) {
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        if (cell.getCellType() == CellType.FORMULA) {
                            formulas.add(sheet.getSheetName() + "!" + cell.getAddress().formatAsString() + "="
                                    + cell.getCellFormula());
                        }
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("读取公式失败: " + file, ex);
        }
        return formulas;
    }

    public static void materialize(Path source, Path target) {
        try (Workbook workbook = WorkbookAccess.open(source)) {
            FormulaEvaluator evaluator = FormulaWriter.evaluator(workbook);
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        if (cell.getCellType() != CellType.FORMULA) {
                            continue;
                        }
                        CellValue value = evaluator.evaluate(cell);
                        if (value == null) {
                            continue;
                        }
                        switch (value.getCellType()) {
                            case STRING -> cell.setCellValue(value.getStringValue());
                            case NUMERIC -> cell.setCellValue(value.getNumberValue());
                            case BOOLEAN -> cell.setCellValue(value.getBooleanValue());
                            default -> cell.setBlank();
                        }
                    }
                }
            }
            WorkbookAccess.write(workbook, target);
        } catch (IOException ex) {
            throw new IllegalStateException("物化公式失败: " + source, ex);
        }
    }

    public static <T> List<T> readEvaluated(Path file, Class<T> head) {
        Path temp = null;
        try {
            temp = Files.createTempFile("formula-", ".xlsx");
            materialize(file, temp);
            return ExcelUtils.read(temp, head);
        } catch (IOException ex) {
            throw new IllegalStateException("读取公式结果失败: " + file, ex);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    temp.toFile().deleteOnExit();
                }
            }
        }
    }
}
