package cn.slainc.excel.print;

import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrintSettingsWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void appliesPageSetupPrintAreaTitlesHeaderFooterAndBreaks() throws Exception {
        Path file = dataWorkbook("print.xlsx");

        PrintSettingsWriter.apply(file, PrintSettings.defaults()
                .paperSize(PaperSize.A3)
                .orientation(PageOrientation.LANDSCAPE)
                .scale(80)
                .printArea("A1:C10")
                .repeatTitleRows(1, 1)
                .repeatTitleColumns(1, 1)
                .headerCenter("&A 月度报表")
                .footerLeft("第 &P 页")
                .footerRight("共 &N 页")
                .rowBreak(5)
                .columnBreak(2)
                .marginLeft(0.5)
                .marginTop(0.8)
                .printGridlines(true)
                .horizontallyCenter(true)
                .verticallyCenter(true));

        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals(PrintSetup.A3_PAPERSIZE, sheet.getPrintSetup().getPaperSize());
            assertTrue(sheet.getPrintSetup().getLandscape());
            assertEquals(80, sheet.getPrintSetup().getScale());
            assertTrue(workbook.getPrintArea(0).contains("A1:C10"), workbook.getPrintArea(0));
            assertNotNull(sheet.getRepeatingRows());
            assertEquals(0, sheet.getRepeatingRows().getFirstRow());
            assertNotNull(sheet.getRepeatingColumns());
            assertEquals(0, sheet.getRepeatingColumns().getFirstColumn());
            assertEquals("&A 月度报表", sheet.getHeader().getCenter());
            assertEquals("第 &P 页", sheet.getFooter().getLeft());
            assertEquals("共 &N 页", sheet.getFooter().getRight());
            assertTrue(Arrays.stream(sheet.getRowBreaks()).anyMatch(value -> value == 5));
            assertTrue(Arrays.stream(sheet.getColumnBreaks()).anyMatch(value -> value == 2));
            assertEquals(0.5d, sheet.getMargin(PageMargin.LEFT), 0.0001d);
            assertEquals(0.8d, sheet.getMargin(PageMargin.TOP), 0.0001d);
            assertTrue(sheet.isPrintGridlines());
            assertTrue(sheet.getHorizontallyCenter());
            assertTrue(sheet.getVerticallyCenter());
        }
    }

    @Test
    void appliesFitToPageOnSecondSheet() throws Exception {
        Path file = dataWorkbook("fit.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
            workbook.createSheet("第二页");
            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }

        PrintSettingsWriter.apply(file, 1, PrintSettings.defaults().fitToPage(1, 3).paperSize(PaperSize.A4));

        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            Sheet sheet = workbook.getSheetAt(1);
            assertTrue(sheet.getFitToPage());
            assertEquals(1, sheet.getPrintSetup().getFitWidth());
            assertEquals(3, sheet.getPrintSetup().getFitHeight());
        }
    }

    @Test
    void validatesPrintSettings() {
        assertThrows(IllegalArgumentException.class, () -> PrintSettings.defaults().scale(5));
        assertThrows(IllegalArgumentException.class, () -> PrintSettings.defaults().fitToPage(0, 1));
        assertThrows(IllegalArgumentException.class, () -> PrintSettings.defaults().repeatTitleRows(3, 1));
        assertThrows(IllegalArgumentException.class, () -> PrintSettings.defaults().repeatTitleColumns(0, 1));
        assertThrows(IllegalArgumentException.class, () -> PrintSettings.defaults().printArea(" "));
        assertThrows(IllegalArgumentException.class, () -> PrintSettingsWriter.apply(Path.of("不存在的文件.xlsx"), null));
    }

    private Path dataWorkbook(String name) throws Exception {
        Path file = tempDir.resolve(name);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("数据");
            for (int index = 0; index < 20; index++) {
                Row row = sheet.createRow(index);
                row.createCell(0).setCellValue("行" + index);
                row.createCell(1).setCellValue(index);
                row.createCell(2).setCellValue(index * 1.5d);
            }
            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }
        return file;
    }
}
