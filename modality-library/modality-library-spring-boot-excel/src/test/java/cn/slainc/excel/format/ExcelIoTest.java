package cn.slainc.excel.format;

import cn.slainc.excel.UserRow;
import cn.slainc.excel.UserStatus;
import cn.slainc.excel.converter.DictRegistry;
import cn.slainc.excel.csv.CsvOptions;
import cn.slainc.excel.write.SheetOptions;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelIoTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        DictRegistry.clear();
        DictRegistry.registerEnum("user_status", UserStatus.class);
    }

    @Test
    void writesAndReadsLegacyXls() throws Exception {
        Path file = tempDir.resolve("legacy.xls");
        ExcelIo.write(file, UserRow.class, rows(), "用户",
                SheetOptions.defaults().freeze(1, 0).columnWidth(0, 20).dropdown(2, List.of("正常", "锁定")));

        assertEquals(ExcelFormat.XLS, ExcelIo.formatOf(file));
        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            assertTrue(workbook instanceof HSSFWorkbook, "应写出老格式 .xls");
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("姓名", sheet.getRow(0).getCell(0).getStringCellValue());
            assertTrue(sheet.getPaneInformation().isFreezePane());
            assertEquals(20 * 256, sheet.getColumnWidth(0));
            assertEquals(1, sheet.getDataValidations().size());
        }

        List<UserRow> read = ExcelIo.read(file, UserRow.class);
        assertEquals(2, read.size());
        assertEquals("张三", read.get(0).getName());
        assertEquals(20, read.get(0).getAge());
        assertEquals(UserStatus.ACTIVE, read.get(0).getStatus());
        assertEquals(UserStatus.LOCKED, read.get(1).getStatus());
    }

    @Test
    void keepsXlsxAndCsvUnaffected() {
        Path xlsx = tempDir.resolve("data.xlsx");
        ExcelIo.write(xlsx, UserRow.class, rows(), "用户");
        assertEquals(ExcelFormat.XLSX, ExcelIo.formatOf(xlsx));
        assertEquals(2, ExcelIo.read(xlsx, UserRow.class).size());
        assertEquals("李四", ExcelIo.read(xlsx, UserRow.class).get(1).getName());

        Path csv = tempDir.resolve("data.csv");
        ExcelIo.writeCsv(csv, UserRow.class, rows(), CsvOptions.defaults());
        assertEquals(ExcelFormat.CSV, ExcelIo.formatOf(csv));
        List<UserRow> read = ExcelIo.read(csv, UserRow.class);
        assertEquals(2, read.size());
        assertEquals("张三", read.get(0).getName());
        assertEquals(20, read.get(0).getAge());
        assertEquals(UserStatus.LOCKED, read.get(1).getStatus());
    }

    @Test
    void detectsFormatByMagicBytes() throws Exception {
        Path xlsx = tempDir.resolve("magic.xlsx");
        ExcelIo.write(xlsx, UserRow.class, rows(), "用户");
        Path xls = tempDir.resolve("magic.xls");
        ExcelIo.write(xls, UserRow.class, rows(), "用户");

        assertEquals(ExcelFormat.XLSX, ExcelFormat.detect(Files.readAllBytes(xlsx)));
        assertEquals(ExcelFormat.XLS, ExcelFormat.detect(Files.readAllBytes(xls)));
        assertEquals(ExcelFormat.CSV, ExcelFormat.detect("姓名,年龄".getBytes(StandardCharsets.UTF_8)));
        assertEquals(ExcelFormat.UNKNOWN, ExcelFormat.detect(new byte[]{0, 1, 2}));
        assertEquals(ExcelFormat.UNKNOWN, ExcelFormat.fromFileName("报表"));
        assertEquals(ExcelFormat.XLSX, ExcelFormat.fromFileName("报表.XLSX"));
        assertEquals("xlsx", ExcelFormat.XLSX.extension());
        assertTrue(ExcelFormat.XLSX.isExcel());
        assertTrue(!ExcelFormat.CSV.isExcel());
    }

    @Test
    void readsCsvWithSemicolonAndGbk() {
        Path file = tempDir.resolve("gbk.csv");
        ExcelIo.writeCsv(file, UserRow.class, rows(), CsvOptions.of(';', java.nio.charset.Charset.forName("GBK")));

        assertEquals(ExcelFormat.CSV, ExcelIo.formatOf(file));
        List<UserRow> read = ExcelIo.readCsv(file, UserRow.class, CsvOptions.of(';', java.nio.charset.Charset.forName("GBK")));
        assertEquals(2, read.size());
        assertEquals("张三", read.get(0).getName());
        assertEquals(UserStatus.ACTIVE, read.get(0).getStatus());
    }

    @Test
    void rejectsUnknownFormat() {
        Path binary = tempDir.resolve("notes.txt");
        try {
            Files.write(binary, new byte[]{0, 1, 2, 3});
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        assertEquals(ExcelFormat.UNKNOWN, ExcelFormat.of(binary));
        assertThrows(IllegalArgumentException.class, () -> ExcelIo.read(binary, UserRow.class));
        assertEquals(ExcelFormat.UNKNOWN, ExcelFormat.of(null));

        Path text = tempDir.resolve("plain.csv");
        try {
            Files.writeString(text, "姓名,年龄");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        assertEquals(ExcelFormat.CSV, ExcelFormat.of(text));
        assertTrue(ExcelIo.read(text, UserRow.class).isEmpty());
    }

    private List<UserRow> rows() {
        return List.of(new UserRow("张三", 20, UserStatus.ACTIVE), new UserRow("李四", 30, UserStatus.LOCKED));
    }
}
