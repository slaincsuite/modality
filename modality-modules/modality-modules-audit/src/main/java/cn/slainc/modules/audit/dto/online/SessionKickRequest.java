package cn.slainc.modules.audit.dto.online;

public record SessionKickRequest(String type, String sessionId, String reason) {
}
