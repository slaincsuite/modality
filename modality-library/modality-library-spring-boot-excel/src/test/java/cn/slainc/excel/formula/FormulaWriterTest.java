package cn.slainc.excel.formula;

import cn.idev.excel.annotation.ExcelProperty;
import cn.slainc.excel.style.ConditionalFormatSpec;
import cn.slainc.excel.style.SheetStyler;
import cn.slainc.excel.style.StyleBuilder;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FormulaWriterTest {

    @TempDir
    Path tempDir;

    public static class FormulaRow {

        @ExcelProperty("项目")
        private String item;

        @ExcelProperty("金额")
        private BigDecimal amount;

        public FormulaRow() {
        }

        public String getItem() {
            return item;
        }

        public void setItem(String item) {
            this.item = item;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }

    @Test
    void writesFormulaAndEvaluatesWithPoiEvaluator() throws Exception {
        Path file = tempDir.resolve("formula.xlsx");
        FormulaWriter.write(file, formulaSheet());

        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            Cell cell = workbook.getSheetAt(0).getRow(4).getCell(1);
            assertEquals(CellType.FORMULA, cell.getCellType());
            assertEquals("SUM(B2:B4)", cell.getCellFormula());
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            assertEquals(60.0d, evaluator.evaluate(cell).getNumberValue());
            assertNotNull(workbook.getSheetAt(0).getRow(0).getCell(0).getCellStyle());
        }

        assertEquals(60.0d, FormulaCalculator.evaluateCell(file, "B5"));
        assertEquals("=SUM(B2:B4)", FormulaCalculator.formulaOf(file, "B5"));
        assertNull(FormulaCalculator.formulaOf(file, "B2"));
        assertFalse(FormulaCalculator.formulas(file).isEmpty());

        Map<String, Object> values = FormulaCalculator.evaluate(file);
        assertEquals(60.0d, values.get("报表!B5"));
    }

    @Test
    void keepsFormulaWhenWorkbookIsRestyled() throws Exception {
        Path file = tempDir.resolve("kept.xlsx");
        FormulaWriter.write(file, formulaSheet());

        SheetStyler.applyRowStyle(file, 0, 4, StyleBuilder.bold());
        SheetStyler.addConditionalFormat(file, 0, ConditionalFormatSpec.between("B2:B4", "0", "25"));

        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            Cell cell = workbook.getSheetAt(0).getRow(4).getCell(1);
            assertEquals(CellType.FORMULA, cell.getCellType());
            assertEquals("SUM(B2:B4)", cell.getCellFormula());
            assertNotNull(cell.getCellStyle());
            assertNotNull(((org.apache.poi.xssf.usermodel.XSSFCellStyle) cell.getCellStyle()).getFont());
            assertEquals(1, SheetStyler.conditionalFormatCount(file, 0));
        }
    }

    @Test
    void evaluatesFormulasBeforeImport() {
        Path file = tempDir.resolve("import.xlsx");
        FormulaWriter.write(file, formulaSheet());

        List<FormulaRow> rows = FormulaCalculator.readEvaluated(file, FormulaRow.class);
        assertEquals(4, rows.size());
        assertEquals("合计", rows.get(3).getItem());
        assertEquals(0, new BigDecimal("60").compareTo(rows.get(3).getAmount()));
        assertEquals(0, new BigDecimal("10").compareTo(rows.get(0).getAmount()));
    }

    @Test
    void keepsProvidedCachedValue() throws Exception {
        Path file = tempDir.resolve("cached.xlsx");
        FormulaWriter.write(file, FormulaSheet.of("缓存", List.of("值"), List.of(List.of(Formula.of("1+1", 42)))));

        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            Cell cell = workbook.getSheetAt(0).getRow(1).getCell(0);
            assertEquals(CellType.FORMULA, cell.getCellType());
            assertEquals("1+1", cell.getCellFormula());
            assertEquals("42", ((XSSFCell) cell).getCTCell().getV());
        }
    }

    private FormulaSheet formulaSheet() {
        return FormulaSheet.of("报表", List.of("项目", "金额"), List.of(
                List.of("A", 10),
                List.of("B", 20),
                List.of("C", 30),
                List.of("合计", Formula.of("SUM(B2:B4)"))));
    }
}
