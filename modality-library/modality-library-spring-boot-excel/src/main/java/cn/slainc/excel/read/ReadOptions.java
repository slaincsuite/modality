package cn.slainc.excel.read;

import java.util.List;
import java.util.function.Consumer;

public record ReadOptions(int headRowNumber, int batchSize, boolean ignoreEmptyRow, boolean stopOnError,
                          Integer sheetNo, String sheetName, Consumer<List<?>> batchConsumer) {

    public static final int DEFAULT_BATCH_SIZE = 1000;

    public static ReadOptions defaults() {
        return new ReadOptions(1, DEFAULT_BATCH_SIZE, true, false, null, null, null);
    }

    public ReadOptions withBatchSize(int size) {
        return new ReadOptions(headRowNumber, size, ignoreEmptyRow, stopOnError, sheetNo, sheetName, batchConsumer);
    }

    public ReadOptions withStopOnError(boolean stop) {
        return new ReadOptions(headRowNumber, batchSize, ignoreEmptyRow, stop, sheetNo, sheetName, batchConsumer);
    }

    @SuppressWarnings("unchecked")
    public <T> ReadOptions withBatchConsumer(Consumer<List<T>> consumer) {
        return new ReadOptions(headRowNumber, batchSize, ignoreEmptyRow, stopOnError, sheetNo, sheetName,
                (Consumer<List<?>>) (Consumer<?>) consumer);
    }
}
