package cn.slainc.services.livemonitor.dto;

import cn.slainc.services.livemonitor.entity.LiveStreamRecord;

public record LiveStreamView(
        Long id,
        String name,
        String url,
        boolean enabled,
        String status,
        Long lastCheckAt,
        Long lastLatencyMs,
        String lastError,
        Long createTime,
        Long updateTime) {

    public static LiveStreamView from(LiveStreamRecord record) {
        return new LiveStreamView(record.getId(), record.getName(), record.getUrl(),
                Boolean.TRUE.equals(record.getEnabled()), record.getStatus(), record.getLastCheckAt(),
                record.getLastLatencyMs(), record.getLastError(), record.getCreateTime(), record.getUpdateTime());
    }
}
