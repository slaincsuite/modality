package cn.slainc.excel.center;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class JdbcExportTaskStore implements ExportTaskStore {

    private static final String TABLE = "t_export_task";

    private static final String COLUMNS = "task_id, task_name, status, sheet_name, head_class, file_name, file_path, "
            + "total_rows, processed_rows, error_message, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ExportTask> rowMapper = this::mapRow;

    public JdbcExportTaskStore(JdbcTemplate jdbcTemplate) {
        if (jdbcTemplate == null) {
            throw new IllegalArgumentException("JdbcTemplate 不能为空");
        }
        this.jdbcTemplate = jdbcTemplate;
        initSchema();
    }

    public void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "task_id VARCHAR(64) PRIMARY KEY, "
                + "task_name VARCHAR(200), "
                + "status VARCHAR(32) NOT NULL, "
                + "sheet_name VARCHAR(200), "
                + "head_class VARCHAR(255), "
                + "file_name VARCHAR(255), "
                + "file_path VARCHAR(1000), "
                + "total_rows BIGINT DEFAULT 0, "
                + "processed_rows BIGINT DEFAULT 0, "
                + "error_message VARCHAR(2000), "
                + "created_at TIMESTAMP, "
                + "updated_at TIMESTAMP)");
    }

    @Override
    public ExportTask save(ExportTask task) {
        requireTask(task);
        jdbcTemplate.update("INSERT INTO " + TABLE + " (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                task.getTaskId(), task.getTaskName(), task.getStatus().name(), task.getSheetName(),
                task.getHeadClassName(), task.getFileName(), task.getFilePath(), task.getTotalRows(),
                task.getProcessedRows(), task.getErrorMessage(), timestamp(task.getCreatedAt()),
                timestamp(task.getUpdatedAt()));
        return task;
    }

    @Override
    public ExportTask update(ExportTask task) {
        requireTask(task);
        int updated = jdbcTemplate.update("UPDATE " + TABLE + " SET task_name = ?, status = ?, sheet_name = ?, "
                        + "head_class = ?, file_name = ?, file_path = ?, total_rows = ?, processed_rows = ?, "
                        + "error_message = ?, updated_at = ? WHERE task_id = ?",
                task.getTaskName(), task.getStatus().name(), task.getSheetName(), task.getHeadClassName(),
                task.getFileName(), task.getFilePath(), task.getTotalRows(), task.getProcessedRows(),
                task.getErrorMessage(), timestamp(task.getUpdatedAt()), task.getTaskId());
        if (updated == 0) {
            throw new IllegalArgumentException("导出任务不存在: " + task.getTaskId());
        }
        return task;
    }

    @Override
    public ExportTask find(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        List<ExportTask> tasks = jdbcTemplate.query("SELECT " + COLUMNS + " FROM " + TABLE + " WHERE task_id = ?",
                rowMapper, taskId);
        return tasks.isEmpty() ? null : tasks.get(0);
    }

    @Override
    public List<ExportTask> list(ExportTaskQuery query) {
        ExportTaskQuery actual = query == null ? ExportTaskQuery.all() : query;
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM " + TABLE + " WHERE 1 = 1");
        List<Object> arguments = new ArrayList<>();
        if (actual.statuses() != null && !actual.statuses().isEmpty()) {
            sql.append(" AND status IN (").append("?,".repeat(actual.statuses().size() - 1)).append("?)");
            actual.statuses().forEach(status -> arguments.add(status.name()));
        }
        if (actual.nameContains() != null && !actual.nameContains().isBlank()) {
            sql.append(" AND LOWER(task_name) LIKE ?");
            arguments.add("%" + actual.nameContains().toLowerCase(java.util.Locale.ROOT) + "%");
        }
        sql.append(" ORDER BY created_at DESC");
        if (actual.limit() != null) {
            sql.append(" LIMIT ?");
            arguments.add(actual.limit());
            if (actual.offset() != null) {
                sql.append(" OFFSET ?");
                arguments.add(actual.offset());
            }
        }
        return jdbcTemplate.query(sql.toString(), rowMapper, arguments.toArray());
    }

    @Override
    public boolean delete(String taskId) {
        return taskId != null && jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE task_id = ?", taskId) > 0;
    }

    @Override
    public long clearFinished() {
        return jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE status IN (?,?,?)",
                ExportTaskStatus.SUCCEEDED.name(), ExportTaskStatus.FAILED.name(), ExportTaskStatus.CANCELED.name());
    }

    @Override
    public List<ExportTask> findResumable() {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM " + TABLE + " WHERE status IN (?,?,?) ORDER BY created_at",
                rowMapper, ExportTaskStatus.PENDING.name(), ExportTaskStatus.RUNNING.name(),
                ExportTaskStatus.FAILED.name());
    }

    private ExportTask mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        ExportTask task = new ExportTask();
        task.setTaskId(resultSet.getString("task_id"));
        task.setTaskName(resultSet.getString("task_name"));
        task.setStatus(ExportTaskStatus.of(resultSet.getString("status")));
        task.setSheetName(resultSet.getString("sheet_name"));
        task.setHeadClassName(resultSet.getString("head_class"));
        task.setFileName(resultSet.getString("file_name"));
        task.setFilePath(resultSet.getString("file_path"));
        task.setTotalRows(resultSet.getLong("total_rows"));
        task.setProcessedRows(resultSet.getLong("processed_rows"));
        task.setErrorMessage(resultSet.getString("error_message"));
        task.setCreatedAt(instant(resultSet.getTimestamp("created_at")));
        task.setUpdatedAt(instant(resultSet.getTimestamp("updated_at")));
        return task;
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    private void requireTask(ExportTask task) {
        if (task == null || task.getTaskId() == null || task.getTaskId().isBlank()) {
            throw new IllegalArgumentException("导出任务标识不能为空");
        }
    }
}
