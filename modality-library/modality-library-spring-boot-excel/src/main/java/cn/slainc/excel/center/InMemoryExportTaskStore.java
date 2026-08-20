package cn.slainc.excel.center;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryExportTaskStore implements ExportTaskStore {

    private final Map<String, ExportTask> tasks = new ConcurrentHashMap<>();

    @Override
    public ExportTask save(ExportTask task) {
        requireTask(task);
        tasks.put(task.getTaskId(), task);
        return task;
    }

    @Override
    public ExportTask update(ExportTask task) {
        requireTask(task);
        if (!tasks.containsKey(task.getTaskId())) {
            throw new IllegalArgumentException("导出任务不存在: " + task.getTaskId());
        }
        tasks.put(task.getTaskId(), task);
        return task;
    }

    @Override
    public ExportTask find(String taskId) {
        return taskId == null ? null : tasks.get(taskId);
    }

    @Override
    public List<ExportTask> list(ExportTaskQuery query) {
        ExportTaskQuery actual = query == null ? ExportTaskQuery.all() : query;
        List<ExportTask> matched = tasks.values().stream()
                .filter(task -> actual.statuses() == null || actual.statuses().isEmpty()
                        || actual.statuses().contains(task.getStatus()))
                .filter(task -> actual.nameContains() == null || actual.nameContains().isBlank()
                        || (task.getTaskName() != null
                        && task.getTaskName().toLowerCase(java.util.Locale.ROOT)
                        .contains(actual.nameContains().toLowerCase(java.util.Locale.ROOT))))
                .sorted(Comparator.comparing(ExportTask::getCreatedAt).reversed())
                .toList();
        int offset = actual.offset() == null ? 0 : Math.max(0, actual.offset());
        if (offset >= matched.size()) {
            return List.of();
        }
        int end = actual.limit() == null ? matched.size() : Math.min(matched.size(), offset + actual.limit());
        return List.copyOf(matched.subList(offset, end));
    }

    @Override
    public boolean delete(String taskId) {
        return taskId != null && tasks.remove(taskId) != null;
    }

    @Override
    public long clearFinished() {
        List<String> finished = tasks.values().stream()
                .filter(task -> task.getStatus().isFinished())
                .map(ExportTask::getTaskId)
                .toList();
        finished.forEach(tasks::remove);
        return finished.size();
    }

    @Override
    public List<ExportTask> findResumable() {
        return tasks.values().stream()
                .filter(task -> task.getStatus().isResumable())
                .sorted(Comparator.comparing(ExportTask::getCreatedAt))
                .toList();
    }

    private void requireTask(ExportTask task) {
        if (task == null || task.getTaskId() == null || task.getTaskId().isBlank()) {
            throw new IllegalArgumentException("导出任务标识不能为空");
        }
    }
}
