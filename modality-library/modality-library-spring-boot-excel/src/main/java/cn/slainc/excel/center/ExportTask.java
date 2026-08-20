package cn.slainc.excel.center;

import java.time.Instant;

public class ExportTask {

    private String taskId;
    private String taskName;
    private ExportTaskStatus status = ExportTaskStatus.PENDING;
    private String sheetName;
    private String headClassName;
    private String fileName;
    private String filePath;
    private long totalRows;
    private long processedRows;
    private String errorMessage;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public ExportTaskStatus getStatus() {
        return status;
    }

    public void setStatus(ExportTaskStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public String getHeadClassName() {
        return headClassName;
    }

    public void setHeadClassName(String headClassName) {
        this.headClassName = headClassName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(long totalRows) {
        this.totalRows = totalRows;
    }

    public long getProcessedRows() {
        return processedRows;
    }

    public void setProcessedRows(long processedRows) {
        this.processedRows = processedRows;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getProgressPercent() {
        if (totalRows <= 0) {
            return status == ExportTaskStatus.SUCCEEDED ? 100 : 0;
        }
        return (int) Math.min(100, processedRows * 100 / totalRows);
    }
}
