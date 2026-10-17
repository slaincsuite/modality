package cn.slainc.services.livemonitor.dto;

public record LiveStreamStatsView(
        long total,
        long online,
        long offline,
        long unknown,
        long averageLatencyMs) {
}
