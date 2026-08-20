package cn.slainc.excel.imports;

import java.time.Instant;

public record ImportLogEntry(String logId, String fileName, String headClassName, int totalRows, int successCount,
                             int errorCount, long durationMillis, boolean success, String message,
                             Instant createdAt) {

    public String describe() {
        return "文件 " + fileName + " 共 " + totalRows + " 行，成功 " + successCount + " 行，失败 " + errorCount
                + " 行，耗时 " + durationMillis + " 毫秒";
    }
}
