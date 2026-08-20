package cn.slainc.excel.center;

import cn.slainc.excel.UserRow;
import cn.slainc.excel.UserStatus;
import cn.slainc.excel.converter.DictRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcExportTaskStoreTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        DictRegistry.clear();
        DictRegistry.registerEnum("user_status", UserStatus.class);
    }

    @Test
    void persistsTaskAndCheckpointAcrossStoreInstances() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        JdbcExportTaskStore store = new JdbcExportTaskStore(jdbcTemplate);
        ExportCenter center = new ExportCenter(store, tempDir.resolve("jdbc"), 50);

        ExportTask task = center.submit(ExportSource.of(UserRow.class, "用户导出", provider(120)));
        assertEquals(ExportTaskStatus.SUCCEEDED, center.execute(task.getTaskId()).getStatus());

        JdbcExportTaskStore reopened = new JdbcExportTaskStore(jdbcTemplate);
        ExportTask loaded = reopened.find(task.getTaskId());
        assertNotNull(loaded);
        assertEquals(ExportTaskStatus.SUCCEEDED, loaded.getStatus());
        assertEquals(120, loaded.getProcessedRows());
        assertEquals("用户导出", loaded.getTaskName());
        assertTrue(loaded.getProgressPercent() == 100);

        assertEquals(1, reopened.list(ExportTaskQuery.all()).size());
        assertEquals(1, reopened.list(ExportTaskQuery.all().withNameContains("用户")).size());
        assertEquals(0, reopened.list(ExportTaskQuery.all().withNameContains("订单")).size());
        assertEquals(1, reopened.list(ExportTaskQuery.all().withLimit(10)).size());
        assertEquals(List.of(), reopened.findResumable());
        assertTrue(center.download(task.getTaskId()).toFile().exists());

        assertEquals(1, center.cleanup());
        assertNull(reopened.find(task.getTaskId()));
    }

    @Test
    void keepsFailedTaskResumable() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        JdbcExportTaskStore store = new JdbcExportTaskStore(jdbcTemplate);
        ExportCenter center = new ExportCenter(store, tempDir.resolve("jdbc-resume"), 40);

        ExportTask task = center.submit(ExportSource.of(UserRow.class, "中断导出", failingProvider(100, 40)));
        ExportTask failed = center.execute(task.getTaskId());
        assertEquals(ExportTaskStatus.FAILED, failed.getStatus());
        assertEquals(40, failed.getProcessedRows());

        List<ExportTask> resumable = new JdbcExportTaskStore(jdbcTemplate).findResumable();
        assertEquals(1, resumable.size());
        assertEquals(task.getTaskId(), resumable.get(0).getTaskId());

        ExportTask resumed = center.resume(task.getTaskId(), ExportSource.of(UserRow.class, "中断导出", provider(100)));
        assertEquals(ExportTaskStatus.SUCCEEDED, resumed.getStatus());
        assertEquals(100, resumed.getProcessedRows());
    }

    private JdbcTemplate jdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:export_center;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        return new JdbcTemplate(dataSource);
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
                    rows.add(new UserRow("用户" + index, (int) (index % 100),
                            index % 2 == 0 ? UserStatus.ACTIVE : UserStatus.LOCKED));
                }
                return rows;
            }
        };
    }

    private ExportDataProvider<UserRow> failingProvider(int total, int failAt) {
        java.util.concurrent.atomic.AtomicBoolean failed = new java.util.concurrent.atomic.AtomicBoolean();
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
}
