package cn.slainc.excel.center;

import cn.slainc.core.exception.ExceptionUtils;
import cn.slainc.excel.format.BeanHead;
import cn.slainc.excel.support.WorkbookAccess;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class ExportCenter {

    public static final int DEFAULT_BATCH_SIZE = 2000;

    public static final int STREAM_WINDOW = 100;

    private static final String PART_MARK = ".part-";

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
        List<ExportTask> finished = Stream.of(ExportTaskStatus.SUCCEEDED, ExportTaskStatus.FAILED,
                        ExportTaskStatus.CANCELED)
                .flatMap(status -> store.list(ExportTaskQuery.of(status)).stream())
                .toList();
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
            long processed = completedRows(file);
            task.setProcessedRows(processed);
            store.update(task);
            while (true) {
                List<?> batch = fetch(source, processed);
                if (batch.isEmpty()) {
                    task.setStatus(ExportTaskStatus.SUCCEEDED);
                    break;
                }
                writePart(file, source, batch, processed, task.getTaskId());
                processed += batch.size();
                task.setProcessedRows(processed);
                store.update(task);
            }
            mergeParts(file, source);
            removeParts(file);
        } catch (RuntimeException ex) {
            task.setStatus(ExportTaskStatus.FAILED);
            String message = ExceptionUtils.getMessage(ex);
            task.setErrorMessage(message == null || message.isBlank() ? "导出失败" : message);
            checkpointQuietly(file, source);
        }
        store.update(task);
        return task;
    }

    @SuppressWarnings("unchecked")
    private List<?> fetch(ExportSource<?> source, long offset) {
        return ((ExportDataProvider<Object>) source.provider()).fetch(offset, batchSize);
    }

    private <T> void writePart(Path file, ExportSource<T> source, List<?> batch, long offset, String taskId) {
        BeanHead<T> head = BeanHead.of(source.head());
        Path part = partPath(file, taskId, offset, batch.size());
        SXSSFWorkbook workbook = new SXSSFWorkbook(STREAM_WINDOW);
        try {
            Sheet sheet = workbook.createSheet(source.sheetName());
            int rowIndex = 0;
            for (Object bean : batch) {
                Row row = sheet.createRow(rowIndex++);
                List<String> values = head.valuesOf((T) bean);
                for (int index = 0; index < values.size(); index++) {
                    row.createCell(index).setCellValue(values.get(index));
                }
            }
            WorkbookAccess.write(workbook, part);
        } finally {
            dispose(workbook);
        }
    }

    private <T> void mergeParts(Path file, ExportSource<T> source) {
        List<Path> parts = partsInOrder(file);
        if (parts.isEmpty()) {
            throw new IllegalStateException("导出任务没有可合并的分片: " + file);
        }
        BeanHead<T> head = BeanHead.of(source.head());
        SXSSFWorkbook target = new SXSSFWorkbook(STREAM_WINDOW);
        try {
            Sheet sheet = target.createSheet(source.sheetName());
            Row headerRow = sheet.createRow(0);
            List<String> headers = head.headers();
            for (int index = 0; index < headers.size(); index++) {
                headerRow.createCell(index).setCellValue(headers.get(index));
            }
            int rowIndex = 1;
            for (Path part : parts) {
                Workbook workbook = WorkbookAccess.open(part);
                try {
                    Sheet partSheet = workbook.getSheetAt(0);
                    for (Row row : partSheet) {
                        Row targetRow = sheet.createRow(rowIndex++);
                        for (int index = 0; index < row.getLastCellNum(); index++) {
                            Cell cell = row.getCell(index);
                            if (cell == null) {
                                continue;
                            }
                            targetRow.createCell(index).setCellValue(cellText(cell));
                        }
                    }
                } finally {
                    close(workbook);
                }
            }
            WorkbookAccess.write(target, file);
        } finally {
            dispose(target);
        }
    }

    private static String cellText(Cell cell) {
        return cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : cell.toString();
    }

    private void checkpointQuietly(Path file, ExportSource<?> source) {
        try {
            if (!partsInOrder(file).isEmpty()) {
                mergeParts(file, source);
            }
        } catch (RuntimeException ignored) {
            // 检查点文件只是可读副本，写失败不影响分片本身的可恢复性
        }
    }

    private long completedRows(Path file) {
        long expected = 0L;
        for (Path part : partsInOrder(file)) {
            long[] meta = parsePart(file, part);
            if (meta == null) {
                removePart(part);
                continue;
            }
            if (meta[0] != expected) {
                removePart(part);
                continue;
            }
            expected += meta[1];
        }
        return expected;
    }

    private List<Path> partsInOrder(Path file) {
        Path directory = file.toAbsolutePath().getParent();
        String prefix = partPrefix(file);
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> parts = new ArrayList<>(stream
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .toList());
            parts.sort(Comparator.comparingLong(path -> offsetOf(parsePart(file, path))));
            return parts;
        } catch (IOException ex) {
            throw new IllegalStateException("读取导出分片失败: " + file, ex);
        }
    }

    private void removeParts(Path file) {
        for (Path part : partsInOrder(file)) {
            removePart(part);
        }
    }

    private void removePart(Path part) {
        try {
            Files.deleteIfExists(part);
        } catch (IOException ex) {
            throw new IllegalStateException("删除导出分片失败: " + part, ex);
        }
    }

    private static long offsetOf(long[] meta) {
        return meta == null ? Long.MAX_VALUE : meta[0];
    }

    private Path partPath(Path file, String taskId, long offset, int rows) {
        return file.toAbsolutePath().getParent().resolve(partPrefix(file) + offset + "-" + rows);
    }

    private static String partPrefix(Path file) {
        return file.getFileName().toString() + PART_MARK;
    }

    private long[] parsePart(Path file, Path part) {
        String prefix = partPrefix(file);
        String name = part.getFileName().toString();
        if (!name.startsWith(prefix)) {
            return null;
        }
        String[] segments = name.substring(prefix.length()).split("-");
        if (segments.length != 2) {
            return null;
        }
        try {
            return new long[]{Long.parseLong(segments[0]), Long.parseLong(segments[1])};
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void dispose(SXSSFWorkbook workbook) {
        try {
            workbook.dispose();
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
        Path file = Path.of(task.getFilePath());
        removeParts(file);
        try {
            Files.deleteIfExists(file);
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
