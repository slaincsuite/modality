package cn.slainc.excel.read;

import java.util.List;
import java.util.function.Consumer;

public record ReadOptions(int headRowNumber, int batchSize, boolean ignoreEmptyRow, boolean validate,
                          boolean stopOnError, Integer sheetNo, String sheetName,
                          Consumer<List<?>> batchConsumer) {

    public static final int DEFAULT_BATCH_SIZE = 1000;

    public static ReadOptions defaults() {
        return new ReadOptions(1, DEFAULT_BATCH_SIZE, true, false, false, null, null, null);
    }

    public ReadOptions withHeadRowNumber(int rows) {
        return new ReadOptions(rows, batchSize, ignoreEmptyRow, validate, stopOnError, sheetNo, sheetName, batchConsumer);
    }

    public ReadOptions withBatchSize(int size) {
        return new ReadOptions(headRowNumber, size, ignoreEmptyRow, validate, stopOnError, sheetNo, sheetName,
                batchConsumer);
    }

    public ReadOptions withValidate(boolean enabled) {
        return new ReadOptions(headRowNumber, batchSize, ignoreEmptyRow, enabled, stopOnError, sheetNo, sheetName,
                batchConsumer);
    }

    public ReadOptions withStopOnError(boolean stop) {
        return new ReadOptions(headRowNumber, batchSize, ignoreEmptyRow, validate, stop, sheetNo, sheetName,
                batchConsumer);
    }

    public ReadOptions withSheet(int sheetNumber) {
        return new ReadOptions(headRowNumber, batchSize, ignoreEmptyRow, validate, stopOnError, sheetNumber, null,
                batchConsumer);
    }

    public ReadOptions withSheet(String name) {
        return new ReadOptions(headRowNumber, batchSize, ignoreEmptyRow, validate, stopOnError, null, name,
                batchConsumer);
    }

    @SuppressWarnings("unchecked")
    public <T> ReadOptions withBatchConsumer(Consumer<List<T>> consumer) {
        return new ReadOptions(headRowNumber, batchSize, ignoreEmptyRow, validate, stopOnError, sheetNo, sheetName,
                (Consumer<List<?>>) (Consumer<?>) consumer);
    }
}
