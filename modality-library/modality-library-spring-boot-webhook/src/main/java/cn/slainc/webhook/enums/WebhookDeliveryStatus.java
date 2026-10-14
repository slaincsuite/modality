package cn.slainc.webhook.enums;

import cn.slainc.utils.string.StringUtils;

import java.util.Locale;

public enum WebhookDeliveryStatus {

    PENDING,

    SUCCESS,

    FAILED,

    EXHAUSTED;

    public boolean terminal() {
        return this == SUCCESS || this == EXHAUSTED;
    }

    public static WebhookDeliveryStatus of(String status) {
        if (StringUtils.isBlank(status)) {
            return PENDING;
        }
        try {
            return valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // 解析失败时退回 PENDING
            return PENDING;
        }
    }
}
