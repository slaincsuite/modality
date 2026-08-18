package cn.slainc.excel.read;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ImportResult<T> {

    private final List<T> records = new ArrayList<>();
    private final List<ImportError> errors = new ArrayList<>();
    private int totalRows;
    private boolean aborted;

    public void addRecord(T record) {
        records.add(record);
    }

    public void addRecords(List<T> batch) {
        if (batch != null) {
            records.addAll(batch);
        }
    }

    public void addError(ImportError error) {
        if (error != null) {
            errors.add(error);
        }
    }

    public void increaseTotalRows() {
        totalRows++;
    }

    public List<T> getRecords() {
        return records;
    }

    public List<ImportError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getSuccessCount() {
        return records.size();
    }

    public int getErrorCount() {
        return errors.size();
    }

    public boolean isSuccess() {
        return errors.isEmpty() && !aborted;
    }

    public boolean isPartialSuccess() {
        return !errors.isEmpty() && !records.isEmpty();
    }

    public boolean isAborted() {
        return aborted;
    }

    public void markAborted() {
        this.aborted = true;
    }

    public String getErrorMessage() {
        if (errors.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(errors.size(), 20);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append("；");
            }
            builder.append(errors.get(i).describe());
        }
        if (errors.size() > limit) {
            builder.append("；...共 ").append(errors.size()).append(" 处错误");
        }
        return builder.toString();
    }
}
