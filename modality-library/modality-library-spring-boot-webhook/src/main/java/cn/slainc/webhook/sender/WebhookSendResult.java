package cn.slainc.webhook.sender;

import cn.slainc.utils.string.StringUtils;

public record WebhookSendResult(Integer httpStatus, String responseBody, String errorMessage) {

    private static final int MAX_BODY_LENGTH = 2000;

    public static WebhookSendResult delivered(int httpStatus, String responseBody) {
        return new WebhookSendResult(httpStatus, truncate(responseBody), null);
    }

    public static WebhookSendResult rejected(int httpStatus, String responseBody, String errorMessage) {
        return new WebhookSendResult(httpStatus, truncate(responseBody), truncate(errorMessage));
    }

    public static WebhookSendResult failed(String errorMessage) {
        return new WebhookSendResult(null, null, truncate(errorMessage));
    }

    public boolean success() {
        return httpStatus != null && httpStatus >= 200 && httpStatus < 300;
    }

    private static String truncate(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        return value.length() <= MAX_BODY_LENGTH ? value : value.substring(0, MAX_BODY_LENGTH);
    }
}
