package cn.slainc.excel;

import cn.slainc.excel.converter.DictRegistry;
import cn.slainc.excel.read.ImportResult;
import cn.slainc.excel.read.ReadOptions;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelUtilsTest {

    @TempDir
    Path tempDir;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @BeforeEach
    void setUp() {
        DictRegistry.clear();
        DictRegistry.registerEnum("user_status", UserStatus.class);
    }

    @Test
    void writesAndReadsBack() {
        Path file = tempDir.resolve("users.xlsx");
        List<UserRow> rows = List.of(
                new UserRow("张三", 20, UserStatus.ACTIVE),
                new UserRow("李四", 30, UserStatus.LOCKED));
        ExcelUtils.write(file, UserRow.class, rows, "用户");

        assertTrue(Files.exists(file));
        List<UserRow> read = ExcelUtils.read(file, UserRow.class);
        assertEquals(2, read.size());
        assertEquals("张三", read.get(0).getName());
        assertEquals(20, read.get(0).getAge());
        assertEquals("李四", read.get(1).getName());
    }

    @Test
    void convertsDictBothWays() {
        Path file = tempDir.resolve("dict.xlsx");
        ExcelUtils.write(file, UserRow.class, List.of(new UserRow("张三", 20, UserStatus.LOCKED)), "用户");

        List<UserRow> read = ExcelUtils.read(file, UserRow.class);
        assertEquals(UserStatus.LOCKED, read.get(0).getStatus());

        assertEquals("正常", DictRegistry.toLabel("user_status", UserStatus.ACTIVE));
        assertEquals(UserStatus.ACTIVE, DictRegistry.toValue("user_status", "正常"));
        assertEquals(UserStatus.LOCKED, DictRegistry.toValue("user_status", "2"));
        assertNull(DictRegistry.toValue("user_status", "不存在的标签"));
    }

    @Test
    void writesAndReadsMultipleSheets() {
        Path file = tempDir.resolve("multi.xlsx");
        Map<String, ExcelUtils.SheetData<?>> sheets = ExcelUtils.sheets();
        sheets.put("第一页", ExcelUtils.SheetData.of(UserRow.class,
                List.of(new UserRow("张三", 20, UserStatus.ACTIVE))));
        sheets.put("第二页", ExcelUtils.SheetData.of(UserRow.class,
                List.of(new UserRow("李四", 30, UserStatus.LOCKED), new UserRow("王五", 40, UserStatus.ACTIVE))));
        ExcelUtils.writeMultiSheet(file, sheets);

        assertEquals(1, ExcelUtils.readSheet(file, UserRow.class, 0).size());
        assertEquals(2, ExcelUtils.readSheet(file, UserRow.class, 1).size());
        assertEquals("李四", ExcelUtils.readSheet(file, UserRow.class, "第二页").get(0).getName());
    }

    @Test
    void reportsValidationErrorsOnImport() {
        Path file = tempDir.resolve("invalid.xlsx");
        ExcelUtils.write(file, UserRow.class, List.of(
                new UserRow("张三", 20, UserStatus.ACTIVE),
                new UserRow(null, 30, UserStatus.ACTIVE),
                new UserRow("李四", -5, UserStatus.ACTIVE)), "用户");

        ImportResult<UserRow> result = ExcelUtils.read(file, UserRow.class,
                ReadOptions.defaults().withValidate(true), validator);

        assertEquals(3, result.getTotalRows());
        assertEquals(1, result.getSuccessCount());
        assertEquals(2, result.getErrorCount());
        assertFalse(result.isSuccess());
        assertTrue(result.isPartialSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("不能为空"));
        assertTrue(result.getErrorMessage().contains("不能为负数"));
    }

    @Test
    void stopsAtFirstErrorWhenConfigured() {
        Path file = tempDir.resolve("stop.xlsx");
        ExcelUtils.write(file, UserRow.class, List.of(
                new UserRow(null, 20, UserStatus.ACTIVE),
                new UserRow("李四", 30, UserStatus.ACTIVE)), "用户");

        ImportResult<UserRow> result = ExcelUtils.read(file, UserRow.class,
                ReadOptions.defaults().withValidate(true).withStopOnError(true), validator);

        assertEquals(1, result.getErrorCount());
        assertEquals(0, result.getSuccessCount());
        assertTrue(result.isAborted());
    }

    @Test
    void invokesBatchConsumer() {
        Path file = tempDir.resolve("batch.xlsx");
        List<UserRow> rows = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            rows.add(new UserRow("用户" + i, i, UserStatus.ACTIVE));
        }
        ExcelUtils.write(file, UserRow.class, rows, "用户");

        List<Integer> batchSizes = new ArrayList<>();
        ImportResult<UserRow> result = ExcelUtils.read(file, UserRow.class,
                ReadOptions.defaults().withBatchSize(10)
                        .withBatchConsumer(batch -> batchSizes.add(batch.size())), null);

        assertEquals(25, result.getSuccessCount());
        assertEquals(List.of(10, 10, 5), batchSizes);
    }

    @Test
    void rejectsEmptySheetDefinition() {
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ExcelUtils.writeMultiSheet(tempDir.resolve("empty.xlsx"), Map.of())).getMessage()
                .contains("至少需要一个 Sheet"));
    }
}
