package cn.slainc.modules.open.entity;

public record AppLogRecord(Long id, String appId, String action, String operator, String reason,
                           String detail, long createTime) {
}
