package cn.slainc.excel;

import cn.slainc.excel.read.ImportResult;
import cn.slainc.excel.read.ReadOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LargeDataTest {

    private static final int ROW_COUNT = 100_000;

    @TempDir
    Path tempDir;

    @Test
    void exportsAndImportsHundredThousandRows() {
        Path file = tempDir.resolve("large.xlsx");
        List<UserRow> rows = new ArrayList<>(ROW_COUNT);
        for (int i = 0; i < ROW_COUNT; i++) {
            rows.add(new UserRow("用户" + i, i % 100, i % 2 == 0 ? UserStatus.ACTIVE : UserStatus.LOCKED));
        }
        ExcelUtils.write(file, UserRow.class, rows, "大数据");

        assertTrue(Files.exists(file), "导出文件应存在");
        long size = size(file);
        assertTrue(size > 0L, "导出文件不应为空");

        rows = null;

        AtomicInteger batches = new AtomicInteger();
        AtomicInteger consumed = new AtomicInteger();
        ImportResult<UserRow> result = ExcelUtils.read(file, UserRow.class,
                ReadOptions.defaults().withBatchSize(5000).withBatchConsumer(batch -> {
                    batches.incrementAndGet();
                    consumed.addAndGet(batch.size());
                }), null);

        assertEquals(ROW_COUNT, result.getSuccessCount());
        assertEquals(ROW_COUNT, result.getTotalRows());
        assertEquals(ROW_COUNT, consumed.get());
        assertEquals(ROW_COUNT / 5000, batches.get());
        assertEquals(0, result.getErrorCount());
        assertEquals("用户0", result.getRecords().get(0).getName());
        assertEquals("用户99999", result.getRecords().get(ROW_COUNT - 1).getName());
    }

    private long size(Path path) {
        try {
            return Files.size(path);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
