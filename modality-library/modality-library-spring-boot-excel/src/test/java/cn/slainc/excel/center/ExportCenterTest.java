package cn.slainc.excel.center;

import cn.slainc.excel.ExcelUtils;
import cn.slainc.excel.UserRow;
import cn.slainc.excel.UserStatus;
import cn.slainc.excel.converter.DictRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportCenterTest {

    private static final int TOTAL = 500;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        DictRegistry.clear();
        DictRegistry.registerEnum("user_status", UserStatus.class);
    }

    @Test
    void submitsCompletesListsDownloadsAndCleans() {
        ExportCenter center = new ExportCenter(new InMemoryExportTaskStore(), tempDir.resolve("center"), 100);
        ExportTask task = center.submit(ExportSource.of(UserRow.class, "用户导出", provider(TOTAL)));

        assertEquals(ExportTaskStatus.PENDING, task.getStatus());
        assertEquals(TOTAL, task.getTotalRows());

        ExportTask finished = center.execute(task.getTaskId());
        assertEquals(ExportTaskStatus.SUCCEEDED, finished.getStatus());
        assertEquals(TOTAL, finished.getProcessedRows());
        assertEquals(100, finished.getProgressPercent());
        assertNull(finished.getErrorMessage());

        assertEquals(1, center.list(ExportTaskQuery.all()).size());
        assertEquals(1, center.list(ExportTaskQuery.of(ExportTaskStatus.SUCCEEDED)).size());
        assertEquals(0, center.list(ExportTaskQuery.of(ExportTaskStatus.FAILED)).size());
        assertEquals(1, center.list(ExportTaskQuery.all().withNameContains("用户")).size());
        assertEquals(0, center.list(ExportTaskQuery.all().withNameContains("订单")).size());
        assertEquals(ExportTaskStatus.SUCCEEDED, center.status(task.getTaskId()).getStatus());

        Path file = center.download(task.getTaskId());
        assertTrue(Files.exists(file));
        List<UserRow> rows = ExcelUtils.read(file, UserRow.class);
        assertEquals(TOTAL, rows.size());
        assertEquals("用户0", rows.get(0).getName());
        assertEquals("用户499", rows.get(TOTAL - 1).getName());
        assertTrue(center.downloadBytes(task.getTaskId()).length > 0);

        assertEquals(1, center.cleanup());
        assertEquals(0, center.list(ExportTaskQuery.all()).size());
        assertFalse(Files.exists(file));
        assertThrows(IllegalArgumentException.class, () -> center.status(task.getTaskId()));
        assertThrows(IllegalArgumentException.class, () -> center.submit(null));
    }

    @Test
    void resumesAfterInterruptionWithPersistedCheckpoint() {
        InMemoryExportTaskStore store = new InMemoryExportTaskStore();
        Path directory = tempDir.resolve("resume");
        ExportCenter first = new ExportCenter(store, directory, 100);

        ExportTask submitted = first.submit(ExportSource.of(UserRow.class, "断点续传", failingProvider(TOTAL, 200)));
        ExportTask failed = first.execute(submitted.getTaskId());

        assertEquals(ExportTaskStatus.FAILED, failed.getStatus());
        assertEquals(200, failed.getProcessedRows(), "检查点应停留在已写入的行数");
        assertNotNull(failed.getErrorMessage());
        assertTrue(failed.getErrorMessage().contains("模拟导出中断"));
        assertEquals(200, ExcelUtils.read(Path.of(failed.getFilePath()), UserRow.class).size());

        ExportCenter restarted = new ExportCenter(store, directory, 100);
        assertEquals(1, restarted.resumable().size(), "未完成的任务应可恢复");
        assertThrows(IllegalStateException.class, () -> restarted.resume(submitted.getTaskId()));

        ExportTask resumed = restarted.resume(submitted.getTaskId(),
                ExportSource.of(UserRow.class, "断点续传", provider(TOTAL)));
        assertEquals(ExportTaskStatus.SUCCEEDED, resumed.getStatus());
        assertEquals(TOTAL, resumed.getProcessedRows());
        assertEquals(TOTAL, restarted.status(submitted.getTaskId()).getProcessedRows());

        List<UserRow> rows = ExcelUtils.read(restarted.download(submitted.getTaskId()), UserRow.class);
        assertEquals(TOTAL, rows.size());
        assertEquals("用户0", rows.get(0).getName());
        assertEquals("用户499", rows.get(TOTAL - 1).getName());
    }

    @Test
    void cancelsPendingTaskAndRemovesTask() {
        ExportCenter center = new ExportCenter(new InMemoryExportTaskStore(), tempDir.resolve("cancel"), 50);
        ExportTask task = center.submit(ExportSource.of(UserRow.class, "待取消", provider(10)));

        assertEquals(ExportTaskStatus.CANCELED, center.cancel(task.getTaskId()).getStatus());
        assertThrows(IllegalStateException.class, () -> center.download(task.getTaskId()));

        ExportTask executed = center.submit(ExportSource.of(UserRow.class, "已完成", provider(10)));
        center.execute(executed.getTaskId());
        assertThrows(IllegalStateException.class, () -> center.cancel(executed.getTaskId()));
        center.remove(executed.getTaskId());
        assertNull(center.getStore().find(executed.getTaskId()));
        assertThrows(IllegalArgumentException.class, () -> center.execute("不存在的任务"));
    }

    private ExportDataProvider<UserRow> provider(int total) {
        return new ExportDataProvider<>() {

            @Override
            public long total() {
                return total;
            }

            @Override
            public List<UserRow> fetch(long offset, int limit) {
                List<UserRow> rows = new ArrayList<>();
                for (long index = offset; index < Math.min(total, offset + limit); index++) {
                    rows.add(row(index));
                }
                return rows;
            }
        };
    }

    private ExportDataProvider<UserRow> failingProvider(int total, int failAt) {
        AtomicBoolean failed = new AtomicBoolean();
        return new ExportDataProvider<>() {

            @Override
            public long total() {
                return total;
            }

            @Override
            public List<UserRow> fetch(long offset, int limit) {
                if (offset >= failAt && failed.compareAndSet(false, true)) {
                    throw new IllegalStateException("模拟导出中断");
                }
                return provider(total).fetch(offset, limit);
            }
        };
    }

    private UserRow row(long index) {
        return new UserRow("用户" + index, (int) (index % 100), index % 2 == 0 ? UserStatus.ACTIVE : UserStatus.LOCKED);
    }
}
