package cn.slainc.excel.chart;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void addsBarLineAndPieCharts() throws Exception {
        Path file = dataWorkbook("charts.xlsx");

        ChartWriter.add(file, ChartSpec.builder().type(ChartType.BAR).title("月度销量")
                .series(ChartSeries.of("销量", "A2:A4", "B2:B4")).anchor(4, 0, 12, 15).build());
        ChartWriter.add(file, ChartSpec.builder().type(ChartType.LINE).title("销量趋势")
                .series(ChartSeries.of("销量", "A2:A4", "B2:B4")).anchor(4, 16, 12, 31).build());
        ChartWriter.add(file, ChartSpec.builder().type(ChartType.PIE).title("销量占比")
                .series(ChartSeries.of("销量", "A2:A4", "B2:B4")).anchor(14, 0, 22, 15).build());

        assertEquals(3, ChartWriter.chartCount(file, 0));
        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            XSSFDrawing drawing = (XSSFDrawing) workbook.getSheetAt(0).getDrawingPatriarch();
            assertEquals(3, drawing.getCharts().size());
            XSSFChart bar = drawing.getCharts().get(0);
            assertEquals(1, bar.getCTChart().getPlotArea().sizeOfBarChartArray());
            assertEquals("月度销量", bar.getTitleText().getString());
            XSSFChart line = drawing.getCharts().get(1);
            assertEquals(1, line.getCTChart().getPlotArea().sizeOfLineChartArray());
            XSSFChart pie = drawing.getCharts().get(2);
            assertEquals(1, pie.getCTChart().getPlotArea().sizeOfPieChartArray());
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
            assertTrue(workbook.getPackage().getParts().stream()
                    .anyMatch(part -> part.getPartName().getName().contains("charts/chart")));
        }
    }

    @Test
    void addsPivotTable() throws Exception {
        Path file = pivotWorkbook("pivot.xlsx");

        PivotWriter.add(file, PivotSpec.builder("A1:C4", "E1")
                .rowLabel(0)
                .dataColumn(PivotFunction.SUM, 2, "金额合计")
                .build());

        assertTrue(PivotWriter.hasPivotPart(file), "应生成透视表部件");
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
            assertTrue(workbook.getPackage().getParts().stream()
                    .anyMatch(part -> part.getPartName().getName().contains("pivotCacheDefinition")));
            assertEquals(1, workbook.getPivotTables().size());
        }
    }

    @Test
    void validatesChartAndPivotInput() {
        assertThrows(IllegalArgumentException.class, () -> ChartSpec.builder().type(ChartType.BAR).build());
        assertThrows(IllegalArgumentException.class,
                () -> ChartSpec.builder().series(ChartSeries.of("销量", "A2:A4", "B2:B4")).build());
        assertThrows(IllegalArgumentException.class, () -> ChartSeries.of("销量", null, "B2:B4"));
        assertThrows(IllegalArgumentException.class, () -> PivotSpec.builder("", "E1"));
        assertThrows(IllegalArgumentException.class, () -> new PivotSpec.DataColumn(null, 0, "合计"));
    }

    private Path dataWorkbook(String name) throws Exception {
        Path file = tempDir.resolve(name);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("数据");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("月份");
            header.createCell(1).setCellValue("销量");
            String[] months = {"一月", "二月", "三月"};
            double[] values = {10, 20, 30};
            for (int index = 0; index < months.length; index++) {
                Row row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(months[index]);
                row.createCell(1).setCellValue(values[index]);
            }
            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }
        return file;
    }

    private Path pivotWorkbook(String name) throws Exception {
        Path file = tempDir.resolve(name);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("明细");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("月份");
            header.createCell(1).setCellValue("产品");
            header.createCell(2).setCellValue("金额");
            String[] months = {"一月", "一月", "二月"};
            String[] products = {"A", "B", "A"};
            double[] amounts = {10, 20, 30};
            for (int index = 0; index < months.length; index++) {
                Row row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(months[index]);
                row.createCell(1).setCellValue(products[index]);
                row.createCell(2).setCellValue(amounts[index]);
            }
            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }
        return file;
    }
}
