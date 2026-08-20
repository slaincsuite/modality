package cn.slainc.excel.center;

import cn.slainc.core.exception.ExceptionUtils;
import cn.slainc.excel.format.BeanHead;
import cn.slainc.excel.support.WorkbookAccess;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExportCenter {

    public static final int DEFAULT_BATCH_SIZE = 2000;

    private final ExportTaskStore store;
    private final Path storageDirectory;
    private final int batchSize;
    private final Map<String, ExportSource<?>> sources = new ConcurrentHashMap<>();

    public ExportCenter(ExportTaskStore store) {
        this(store, defaultDirectory(), DEFAULT_BATCH_SIZE);
    }

    public ExportCenter(ExportTaskStore store, Path storageDirectory, int batchSize) {
        this.store = store == null ? new InMemoryExportTaskStore() : store;
        this.storageDirectory = storageDirectory == null ? defaultDirectory() : storageDirectory;
        this.batchSize = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        try {
            Files.createDirectories(this.storageDirectory);
        } catch (IOException ex) {
            throw new IllegalStateException("创建导出目录失败: " + this.storageDirectory, ex);
        }
    }

    public ExportTaskStore getStore() {
        return store;
    }

    public Path getStorageDirectory() {
        return storageDirectory;
    }

    public <T> ExportTask submit(ExportSource<T> source) {
        if (source == null) {
            throw new IllegalArgumentException("导出数据源不能为空");
        }
        String taskId = UUID.randomUUID().toString().replace("-", "");
        ExportTask task = new ExportTask();
        task.setTaskId(taskId);
        task.setTaskName(source.name());
        task.setSheetName(source.sheetName());
        task.setHeadClassName(source.head().getName());
        task.setFileName(taskId + ".xlsx");
        task.setFilePath(storageDirectory.resolve(task.getFileName()).toString());
        task.setTotalRows(source.provider().total());
        sources.put(taskId, source);
        return store.save(task);
    }

    public ExportTask execute(String taskId) {
        return run(require(taskId));
    }

    public ExportTask resume(String taskId) {
        return run(require(taskId));
    }

    public <T> ExportTask resume(String taskId, ExportSource<T> source) {
        ExportTask task = require(taskId);
        if (source == null) {
            throw new IllegalArgumentException("恢复导出任务必须重新绑定数据源");
        }
        sources.put(taskId, source);
        task.setTaskName(source.name());
        task.setTotalRows(source.provider().total());
        return run(task);
    }

    public ExportTask status(String taskId) {
        return require(taskId);
    }

    public List<ExportTask> list(ExportTaskQuery query) {
        return store.list(query);
    }

    public List<ExportTask> resumable() {
        return store.findResumable();
    }

    public Path download(String taskId) {
        ExportTask task = require(taskId);
        if (task.getStatus() != ExportTaskStatus.SUCCEEDED) {
            throw new IllegalStateException("导出任务尚未完成，无法下载: " + taskId);
        }
        Path file = Path.of(task.getFilePath());
        if (!Files.exists(file)) {
            throw new IllegalStateException("导出文件不存在: " + file);
        }
        return file;
    }

    public byte[] downloadBytes(String taskId) {
        return WorkbookAccess.readAll(download(taskId));
    }

    public ExportTask cancel(String taskId) {
        ExportTask task = require(taskId);
        if (task.getStatus() == ExportTaskStatus.RUNNING) {
            throw new IllegalStateException("执行中的导出任务无法取消: " + taskId);
        }
        if (task.getStatus().isFinished() && task.getStatus() != ExportTaskStatus.CANCELED) {
            throw new IllegalStateException("已结束的导出任务无法取消: " + taskId);
        }
        if (task.getStatus() != ExportTaskStatus.CANCELED) {
            task.setStatus(ExportTaskStatus.CANCELED);
            store.update(task);
        }
        return task;
    }

    public long cleanup() {
        List<ExportTask> finished = new ArrayList<>();
        finished.addAll(store.list(ExportTaskQuery.of(ExportTaskStatus.SUCCEEDED)));
        finished.addAll(store.list(ExportTaskQuery.of(ExportTaskStatus.FAILED)));
        finished.addAll(store.list(ExportTaskQuery.of(ExportTaskStatus.CANCELED)));
        long removed = 0;
        for (ExportTask task : finished) {
            deleteFile(task);
            if (store.delete(task.getTaskId())) {
                sources.remove(task.getTaskId());
                removed++;
            }
        }
        return removed;
    }

    public ExportTask remove(String taskId) {
        ExportTask task = require(taskId);
        deleteFile(task);
        store.delete(taskId);
        sources.remove(taskId);
        return task;
    }

    private ExportTask run(ExportTask task) {
        ExportSource<?> source = sources.get(task.getTaskId());
        if (source == null) {
            throw new IllegalStateException("导出任务的数据源已失效，请重新绑定后恢复: " + task.getTaskId());
        }
        task.setStatus(ExportTaskStatus.RUNNING);
        task.setErrorMessage(null);
        store.update(task);
        Path file = Path.of(task.getFilePath());
        try {
            task.setTotalRows(source.provider().total());
            while (true) {
                List<?> batch = fetch(source, task.getProcessedRows());
                if (batch.isEmpty()) {
                    task.setStatus(ExportTaskStatus.SUCCEEDED);
                    break;
                }
                appendBatch(file, source, batch, task.getProcessedRows());
                task.setProcessedRows(task.getProcessedRows() + batch.size());
                store.update(task);
            }
        } catch (RuntimeException ex) {
            task.setStatus(ExportTaskStatus.FAILED);
            String message = ExceptionUtils.getMessage(ex);
            task.setErrorMessage(message == null || message.isBlank() ? "导出失败" : message);
        }
        store.update(task);
        return task;
    }

    @SuppressWarnings("unchecked")
    private List<?> fetch(ExportSource<?> source, long offset) {
        return ((ExportDataProvider<Object>) source.provider()).fetch(offset, batchSize);
    }

    @SuppressWarnings("unchecked")
    private void appendBatch(Path file, ExportSource<?> source, List<?> batch, long offset) {
        append(file, (ExportSource<Object>) source, (List<Object>) batch, offset);
    }

    private <T> void append(Path file, ExportSource<T> source, List<T> batch, long offset) {
        BeanHead<T> head = BeanHead.of(source.head());
        boolean exists = Files.exists(file);
        Workbook workbook = exists ? WorkbookAccess.open(file) : new XSSFWorkbook();
        try {
            Sheet sheet;
            if (exists) {
                sheet = workbook.getSheetAt(0);
            } else {
                sheet = workbook.createSheet(source.sheetName());
                Row headerRow = sheet.createRow(0);
                List<String> headers = head.headers();
                for (int index = 0; index < headers.size(); index++) {
                    headerRow.createCell(index).setCellValue(headers.get(index));
                }
            }
            int rowIndex = (int) offset + 1;
            for (T bean : batch) {
                Row row = sheet.createRow(rowIndex++);
                List<String> values = head.valuesOf(bean);
                for (int index = 0; index < values.size(); index++) {
                    row.createCell(index).setCellValue(values.get(index));
                }
            }
            WorkbookAccess.write(workbook, file);
        } finally {
            close(workbook);
        }
    }

    private void close(Workbook workbook) {
        try {
            workbook.close();
        } catch (IOException ex) {
            throw new IllegalStateException("关闭导出工作簿失败", ex);
        }
    }

    private void deleteFile(ExportTask task) {
        if (task.getFilePath() == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(task.getFilePath()));
        } catch (IOException ex) {
            throw new IllegalStateException("删除导出文件失败: " + task.getFilePath(), ex);
        }
    }

    private ExportTask require(String taskId) {
        ExportTask task = store.find(taskId);
        if (task == null) {
            throw new IllegalArgumentException("导出任务不存在: " + taskId);
        }
        return task;
    }

    private static Path defaultDirectory() {
        return Paths.get(System.getProperty("java.io.tmpdir"), "modality-excel-export");
    }
}
