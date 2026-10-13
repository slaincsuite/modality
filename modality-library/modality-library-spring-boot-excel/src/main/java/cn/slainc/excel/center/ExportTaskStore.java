package cn.slainc.excel.center;

import java.util.List;

public interface ExportTaskStore {

    ExportTask save(ExportTask task);

    ExportTask update(ExportTask task);

    ExportTask find(String taskId);

    List<ExportTask> list(ExportTaskQuery query);

    boolean delete(String taskId);

    List<ExportTask> findResumable();
}
