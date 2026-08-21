package cn.slainc.datasource.manager;

import cn.slainc.datasource.enums.DataSourceStatus;

import java.time.LocalDateTime;

public record DataSourceHealth(String name, DataSourceStatus status, boolean healthy,
                               long elapsedMillis, String message, LocalDateTime checkedAt) {

    public static DataSourceHealth unknown(String name) {
        return new DataSourceHealth(name, DataSourceStatus.UNKNOWN, false, 0L, "尚未探测", null);
    }
}
