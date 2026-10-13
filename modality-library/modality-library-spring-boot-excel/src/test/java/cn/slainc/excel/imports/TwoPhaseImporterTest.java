package cn.slainc.excel.imports;

import cn.slainc.excel.ExcelUtils;
import cn.slainc.excel.UserRow;
import cn.slainc.excel.UserStatus;
import cn.slainc.excel.converter.DictRegistry;
import cn.slainc.excel.read.ImportResult;
import cn.slainc.excel.read.ReadOptions;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwoPhaseImporterTest {

    @TempDir
    Path tempDir;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @BeforeEach
    void setUp() {
        DictRegistry.clear();
        DictRegistry.registerEnum("user_status", UserStatus.class);
    }

    @Test
    void previewsThenConfirmsAndDeduplicates() {
        Path file = workbook("import.xlsx");
        ImportLog log = new ImportLog(new InMemoryImportLogStore(10));
        TwoPhaseImporter importer = importer(Duration.ofMinutes(5), log);

        ImportSession<UserRow> session = importer.preview(file, UserRow.class, 2,
                ReadOptions.defaults());
        assertEquals(2, session.getPreviewCount());
        assertEquals("张三", session.getPreviewRows().get(0).getName());
        assertTrue(session.getPreviewErrors().isEmpty());
        assertEquals(1, importer.sessions().size());
        assertEquals(session.getSessionId(), importer.session(session.getSessionId()).getSessionId());

        UniqueKeyDeduplicator<UserRow> deduplicator = UniqueKeyDeduplicator.ofFields(UserRow.class, "name");
        ImportResult<UserRow> result = importer.confirm(session.getSessionId(), deduplicator);

        assertEquals(4, result.getSuccessCount(), "重复的姓名应被去重");
        assertEquals(1, deduplicator.getDuplicateCount());
        assertEquals(1, result.getErrorCount());
        assertTrue(result.getErrorMessage().contains("唯一键"), result.getErrorMessage());
        assertEquals(0, importer.sessions().size(), "确认后应清理会话文件");

        assertEquals(1, log.size());
        ImportLogEntry entry = log.recent(1).get(0);
        assertFalse(entry.success());
        assertEquals(5, entry.totalRows());
        assertEquals(4, entry.successCount());
        assertTrue(log.all().get(0).describe().contains("import.xlsx"));

        assertThrows(IllegalArgumentException.class, () -> importer.confirm(session.getSessionId()));
        log.clear();
        assertEquals(0, log.size());
    }

    @Test
    void writesImportTemplateWithDropdowns() throws Exception {
        byte[] template = ImportTemplateWriter.toBytes(UserRow.class, "导入模板",
                Map.of(2, List.of("正常", "锁定")), List.of());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("导入模板", sheet.getSheetName());
            assertEquals("姓名", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("年龄", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("状态", sheet.getRow(0).getCell(2).getStringCellValue());
            assertEquals(1, sheet.getDataValidations().size());
            assertTrue(sheet.getPaneInformation().isFreezePane());
            assertTrue(sheet.getColumnWidth(0) >= 10 * 256);
            assertEquals(0, sheet.getLastRowNum(), "模板只包含表头行");
        }
    }

    @Test
    void expiresAndClearsSessions() throws Exception {
        Path file = workbook("expire.xlsx");
        TwoPhaseImporter importer = importer(Duration.ofMillis(1), null);
        ImportSession<UserRow> session = importer.preview(file, UserRow.class, 1, ReadOptions.defaults());

        Thread.sleep(20L);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> importer.session(session.getSessionId()));
        assertTrue(error.getMessage().contains("过期"), error.getMessage());

        ImportSession<UserRow> alive = importer.preview(file, UserRow.class, 1, ReadOptions.defaults());
        importer.discard(alive.getSessionId());
        assertEquals(0, importer.sessions().size());
        assertEquals(0, importer.clearExpired());
    }

    @Test
    void validatesDeduplicatorInput() {
        assertThrows(IllegalArgumentException.class, () -> UniqueKeyDeduplicator.of(null));
        assertThrows(IllegalArgumentException.class, () -> UniqueKeyDeduplicator.ofFields(UserRow.class));
        assertThrows(IllegalArgumentException.class,
                () -> UniqueKeyDeduplicator.ofFields(UserRow.class, "不存在的字段"));
        assertEquals(0, UniqueKeyDeduplicator.ofFields(UserRow.class, "name").getDuplicateCount());
    }

    private TwoPhaseImporter importer(Duration ttl, ImportLog log) {
        return new TwoPhaseImporter(new InMemoryImportSessionStore(tempDir.resolve("sessions"), ttl), validator, log);
    }

    private Path workbook(String name) {
        Path file = tempDir.resolve(name);
        ExcelUtils.write(file, UserRow.class, List.of(
                new UserRow("张三", 20, UserStatus.ACTIVE),
                new UserRow("李四", 30, UserStatus.LOCKED),
                new UserRow("张三", 40, UserStatus.ACTIVE),
                new UserRow("王五", 50, UserStatus.ACTIVE),
                new UserRow("赵六", 60, UserStatus.LOCKED)), "用户");
        return file;
    }
}
