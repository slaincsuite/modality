package cn.slainc.excel;

import cn.slainc.excel.read.ImportResult;
import cn.slainc.excel.read.ReadOptions;
import cn.slainc.excel.template.TemplateExporter;
import cn.slainc.excel.write.ErrorReportRow;
import cn.slainc.excel.write.ErrorReportWriter;
import cn.slainc.excel.write.ExcelImageUtils;
import cn.slainc.excel.write.SheetOptions;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelAdvancedTest {

    @TempDir
    Path tempDir;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        cn.slainc.excel.converter.DictRegistry.clear();
        cn.slainc.excel.converter.DictRegistry.registerEnum("user_status", UserStatus.class);
    }

    @Test
    void appliesSheetOptions() throws Exception {
        Path file = tempDir.resolve("options.xlsx");
        SheetOptions options = SheetOptions.defaults()
                .freeze(1, 0)
                .autoFilter(3)
                .columnWidth(0, 20)
                .columnWidth(1, 12)
                .dropdown(2, List.of("正常", "锁定"));
        ExcelUtils.write(file, UserRow.class,
                List.of(new UserRow("张三", 20, UserStatus.ACTIVE)), "用户", options);

        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertNotNull(sheet.getPaneInformation(), "应设置冻结窗格");
            assertTrue(sheet.getPaneInformation().isFreezePane());
            assertEquals(1, sheet.getPaneInformation().getHorizontalSplitPosition());
            assertTrue(((org.apache.poi.xssf.usermodel.XSSFSheet) sheet).getCTWorksheet().isSetAutoFilter(),
                    "应设置自动筛选");
            assertEquals(20 * 256, sheet.getColumnWidth(0));
            assertEquals(12 * 256, sheet.getColumnWidth(1));
            assertEquals(1, sheet.getDataValidations().size(), "应设置下拉数据验证");
        }
    }

    @Test
    void convertsImages() {
        byte[] raw = new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        String base64 = Base64.getEncoder().encodeToString(raw);
        assertTrue(java.util.Arrays.equals(raw, ExcelImageUtils.fromBase64(base64)));
        assertTrue(java.util.Arrays.equals(raw, ExcelImageUtils.fromBase64("data:image/png;base64," + base64)));

        var cellData = ExcelImageUtils.toCellData(raw);
        assertNotNull(cellData);
        assertEquals(1, cellData.getImageDataList().size());
        assertTrue(java.util.Arrays.equals(raw, cellData.getImageDataList().get(0).getImage()));

        assertNull(ExcelImageUtils.toCellData(new byte[0]));
        assertNull(ExcelImageUtils.fromBase64(null));
        assertNull(ExcelImageUtils.fromUrl(null));
        assertTrue(ExcelImageUtils.isImage("a.PNG"));
        assertTrue(!ExcelImageUtils.isImage("a.txt"));
    }

    @Test
    void fillsTemplateVariables() throws Exception {
        Path template = tempDir.resolve("template.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("报表");
            sheet.createRow(0).createCell(0).setCellValue("标题");
            sheet.getRow(0).createCell(1).setCellValue("{title}");
            sheet.createRow(1).createCell(0).setCellValue("日期");
            sheet.getRow(1).createCell(1).setCellValue("{date}");
            try (OutputStream outputStream = Files.newOutputStream(template)) {
                workbook.write(outputStream);
            }
        }

        Path output = tempDir.resolve("filled.xlsx");
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("title", "月度报表");
        variables.put("date", "2024-01-15");
        TemplateExporter.fill(template, output, variables, null);

        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(output))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("月度报表", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("2024-01-15", sheet.getRow(1).getCell(1).getStringCellValue());
        }
    }

    @Test
    void previewsLimitedRows() {
        Path file = tempDir.resolve("preview.xlsx");
        java.util.List<UserRow> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            rows.add(new UserRow("用户" + i, i, UserStatus.ACTIVE));
        }
        ExcelUtils.write(file, UserRow.class, rows, "用户");

        assertEquals(5, ExcelUtils.preview(file, UserRow.class, 5).size());
        assertEquals(50, ExcelUtils.preview(file, UserRow.class, 999).size());
        assertEquals("用户0", ExcelUtils.preview(file, UserRow.class, 3).get(0).getName());
    }

    @Test
    void writesErrorReport() throws Exception {
        Path source = tempDir.resolve("invalid.xlsx");
        ExcelUtils.write(source, UserRow.class,
                List.of(new UserRow("张三", 20, UserStatus.ACTIVE), new UserRow(null, -1, UserStatus.LOCKED)), "用户");
        ImportResult<UserRow> result = ExcelUtils.read(source, UserRow.class,
                ReadOptions.defaults().withValidate(true), validator);
        assertEquals(2, result.getErrorCount());

        Path report = tempDir.resolve("errors.xlsx");
        ErrorReportWriter.write(report, result);
        List<ErrorReportRow> rows = ExcelUtils.read(report, ErrorReportRow.class);
        assertEquals(2, rows.size());
        assertNotNull(rows.get(0).getRowNumber());
        assertNotNull(rows.get(0).getMessage());

        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(report))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("行号", sheet.getRow(0).getCell(0).getStringCellValue());
            assertTrue(((org.apache.poi.xssf.usermodel.XSSFSheet) sheet).getCTWorksheet().isSetAutoFilter());
            assertTrue(sheet.getPaneInformation().isFreezePane());
        }
    }

    @Test
    void writesErrorReportWithoutErrors() throws Exception {
        Path report = tempDir.resolve("empty-errors.xlsx");
        ErrorReportWriter.write(report, null);
        List<ErrorReportRow> rows = ExcelUtils.read(report, ErrorReportRow.class);
        assertEquals(1, rows.size());
        assertEquals("没有错误", rows.get(0).getMessage());
    }

    @Test
    void reportsUnknownDictLabelAsRowError() throws Exception {
        Path file = tempDir.resolve("unknown-dict.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("用户");
            Row head = sheet.createRow(0);
            head.createCell(0).setCellValue("姓名");
            head.createCell(1).setCellValue("年龄");
            head.createCell(2).setCellValue("状态");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("张三");
            row.createCell(1).setCellValue(20);
            row.createCell(2).setCellValue("未知状态");
            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }

        ImportResult<UserRow> result = ExcelUtils.read(file, UserRow.class, ReadOptions.defaults(), null);
        assertEquals(1, result.getErrorCount());
        assertTrue(result.getErrorMessage().contains("不存在标签"), result.getErrorMessage());
        assertEquals(0, result.getSuccessCount());
    }

    @Test
    void sheetOptionsBuilderDefaults() {
        SheetOptions options = SheetOptions.defaults();
        assertTrue(options.isEmpty());
        options.freeze(1, 2);
        assertEquals(1, options.getFreezeRows());
        assertEquals(2, options.getFreezeColumns());
        assertTrue(!options.isEmpty());
        options.dropdown(0, List.of());
        assertTrue(options.getDropdowns().isEmpty());
    }

    @SuppressWarnings("unused")
    private void rowAnchor(Row row) {
    }

    @SuppressWarnings("unused")
    private void streamAnchor(InputStream inputStream) {
    }
}
