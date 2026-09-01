package cn.slainc.storage.metrics;

public interface StorageMetricsRecorder {

    void recordUpload(String backend, long bytes, boolean success, long costMillis);

    void recordDownload(String backend, long bytes, boolean success, long costMillis);

    void recordDelete(String backend, boolean success);

    void recordFailure(String backend, String operation);

    void recordInstantUpload(boolean hit);

    void recordBackendSwitch(String from, String to);

    void recordStorageSize(String backend, long bytes);

    default void recordMultipart(String backend, int parts) {
    }

    StorageMetricsRecorder NOOP = new StorageMetricsRecorder() {

        @Override
        public void recordUpload(String backend, long bytes, boolean success, long costMillis) {
        }

        @Override
        public void recordDownload(String backend, long bytes, boolean success, long costMillis) {
        }

        @Override
        public void recordDelete(String backend, boolean success) {
        }

        @Override
        public void recordFailure(String backend, String operation) {
        }

        @Override
        public void recordInstantUpload(boolean hit) {
        }

        @Override
        public void recordBackendSwitch(String from, String to) {
        }

        @Override
        public void recordStorageSize(String backend, long bytes) {
        }
    };
}
