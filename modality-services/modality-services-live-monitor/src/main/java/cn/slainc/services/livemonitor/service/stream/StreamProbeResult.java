package cn.slainc.services.livemonitor.service.stream;

public record StreamProbeResult(String status, Long latencyMs, String error) {
}
