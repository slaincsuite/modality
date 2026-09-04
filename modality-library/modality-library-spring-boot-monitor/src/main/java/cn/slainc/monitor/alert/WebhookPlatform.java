package cn.slainc.monitor.alert;

import java.util.Locale;

public enum WebhookPlatform {

    DINGTALK("钉钉"), FEISHU("飞书"), WECOM("企业微信"), ALERT_MANAGER("AlertManager"), CUSTOM("自定义");

    private final String description;

    WebhookPlatform(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static WebhookPlatform parse(String value, WebhookPlatform defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        switch (normalized) {
            case "DING", "DINGTALK", "DING_TALK" -> {
                return DINGTALK;
            }
            case "LARK", "FEISHU" -> {
                return FEISHU;
            }
            case "WECHAT", "WECHAT_WORK", "QYWX", "WECOM", "WE_COM" -> {
                return WECOM;
            }
            case "ALERTMANAGER", "ALERT_MANAGER", "PROMETHEUS" -> {
                return ALERT_MANAGER;
            }
            default -> {
                return CUSTOM;
            }
        }
    }
}
