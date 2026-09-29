package cn.slainc.modules.audit.alert;


// 审计告警渠道：默认只有 Slf4j 实现；monitor 在类路径时由桥实现转发到平台告警体系（钉钉/飞书/企微 webhook 由 monitor 配置）
public interface AuditAlertChannel {

    String name();

    void send(AuditAlertMessage message);
}
