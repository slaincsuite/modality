package cn.slainc.excel.style;

import cn.idev.excel.annotation.ExcelProperty;
import cn.slainc.excel.ExcelUtils;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyleWriterTest {

    @TempDir
    Path tempDir;

    public static class StyledRow {

        @ExcelProperty("姓名")
        @ExcelStyle(fontName = "宋体", fontSize = 16, bold = true, italic = true, underline = true,
                fontColor = "#FF0000", foregroundColor = "#FFFF00", border = BorderWeight.THIN,
                borderColor = "#0000FF", horizontal = CellAlign.CENTER, vertical = VerticalAlign.CENTER,
                wrapText = true, indent = 2)
        private String name;

        @ExcelProperty("金额")
        @ExcelStyle(numberPattern = NumberPattern.MONEY, horizontal = CellAlign.RIGHT)
        private BigDecimal amount;

        @ExcelProperty("占比")
        @ExcelStyle(numberPattern = NumberPattern.PERCENT)
        private BigDecimal ratio;

        public StyledRow() {
        }

        public StyledRow(String name, BigDecimal amount, BigDecimal ratio) {
            this.name = name;
            this.amount = amount;
            this.ratio = ratio;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public BigDecimal getRatio() {
            return ratio;
        }

        public void setRatio(BigDecimal ratio) {
            this.ratio = ratio;
        }
    }

    @Test
    void appliesAnnotatedStylesToCells() throws Exception {
        Path file = tempDir.resolve("styled.xlsx");
        StyledExporter.write(file, StyledRow.class,
                List.of(new StyledRow("张三", new BigDecimal("1234.5"), new BigDecimal("0.256"))),
                StyledWriteOptions.defaults().sheetName("样式").headerStyle(StyleBuilder.header()).rowHeight(24F));

        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            XSSFCellStyle headerStyle = (XSSFCellStyle) sheet.getRow(0).getCell(0).getCellStyle();
            assertTrue(headerStyle.getFont().getBold(), "表头应加粗");
            assertEquals(HorizontalAlignment.CENTER, headerStyle.getAlignment());
            assertArrayEquals(StyleApplier.xssfColor("#D9E1F2").getRGB(),
                    headerStyle.getFillForegroundColorColor().getRGB());
            assertEquals(24F, sheet.getRow(1).getHeightInPoints());

            XSSFCellStyle style = (XSSFCellStyle) sheet.getRow(1).getCell(0).getCellStyle();
            XSSFFont font = (XSSFFont) style.getFont();
            assertEquals("宋体", font.getFontName());
            assertEquals(16, (int) font.getFontHeightInPoints());
            assertTrue(font.getBold());
            assertTrue(font.getItalic());
            assertEquals(Font.U_SINGLE, font.getUnderline());
            assertArrayEquals(StyleApplier.xssfColor("#FF0000").getRGB(), font.getXSSFColor().getRGB());
            assertArrayEquals(StyleApplier.xssfColor("#FFFF00").getRGB(),
                    style.getFillForegroundColorColor().getRGB());
            assertEquals(BorderStyle.THIN, style.getBorderTop());
            assertEquals(BorderStyle.THIN, style.getBorderRight());
            assertArrayEquals(StyleApplier.xssfColor("#0000FF").getRGB(),
                    style.getTopBorderXSSFColor().getRGB());
            assertEquals(HorizontalAlignment.CENTER, style.getAlignment());
            assertEquals(VerticalAlignment.CENTER, style.getVerticalAlignment());
            assertTrue(style.getWrapText());
            assertEquals(2, (int) style.getIndention());

            Cell amountCell = sheet.getRow(1).getCell(1);
            assertEquals("¥#,##0.00", amountCell.getCellStyle().getDataFormatString());
            assertEquals(HorizontalAlignment.RIGHT, amountCell.getCellStyle().getAlignment());
            assertEquals(1234.5d, amountCell.getNumericCellValue());
            assertEquals("0.00%", sheet.getRow(1).getCell(2).getCellStyle().getDataFormatString());
        }
    }

    @Test
    void appliesRowStyleAndConditionalFormat() throws Exception {
        Path file = tempDir.resolve("row-styles.xlsx");
        ExcelUtils.write(file, StyledRow.class,
                List.of(new StyledRow("张三", new BigDecimal("10"), new BigDecimal("0.1")),
                        new StyledRow("李四", new BigDecimal("200"), new BigDecimal("0.9"))), "数据");

        SheetStyler.applyRowStyle(file, 0, 2, StyleBuilder.warning());
        SheetStyler.applyRowHeight(file, 0, 2, 30F);
        SheetStyler.addConditionalFormat(file, 0, ConditionalFormatSpec.greaterThan("B2:B3", "100"));

        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            XSSFCellStyle style = (XSSFCellStyle) sheet.getRow(2).getCell(0).getCellStyle();
            assertTrue(style.getFont().getBold(), "整行样式应作用到该行每个单元格");
            assertArrayEquals(StyleApplier.xssfColor("#FFC7CE").getRGB(),
                    style.getFillForegroundColorColor().getRGB());
            assertEquals(30F, sheet.getRow(2).getHeightInPoints());
            assertNotNullCellStyle(sheet.getRow(2).getCell(2));
            assertEquals(1, SheetStyler.conditionalFormatCount(file, 0));
            assertEquals(List.of("B2:B3"), SheetStyler.conditionalFormatRanges(file, 0));
        }
    }

    @Test
    void buildsPresetsAndValidatesInput() {
        assertEquals(NumberPattern.MONEY, StyleBuilder.money().getNumberPattern());
        assertEquals("0.00%", StyleBuilder.percent().pattern());
        assertTrue(StyleBuilder.header().getBold());
        assertEquals(BorderWeight.THIN, StyleBuilder.bordered().getBorder());
        assertTrue(StyleBuilder.wrapped().getWrapText());
        assertEquals(20, (int) StyleBuilder.of().fontSize(20).build().getFontSize());

        StyleSpec merged = StyleSpec.builder().bold(true).build().over(StyleSpec.builder().foregroundColor("#EEEEEE").build());
        assertTrue(merged.getBold());
        assertEquals("#EEEEEE", merged.getForegroundColor());

        assertFalse(StyleSpec.empty().hasFont());
        assertTrue(StyleSpec.empty().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> StyleSpec.builder().fontSize(0));
        assertThrows(IllegalArgumentException.class, () -> StyleSpec.builder().rotation(120));
        assertThrows(IllegalArgumentException.class, () -> StyleApplier.parse("#GGGGGG"));
        assertThrows(IllegalArgumentException.class, () -> StyleApplier.parse("#12345"));
        assertEquals(0xFF, StyleApplier.parse("#F00").getRed());
        assertEquals(0x00, StyleApplier.parse("red").getGreen());
    }

    private void assertNotNullCellStyle(Cell cell) {
        assertTrue(cell.getCellStyle() != null);
    }
}
