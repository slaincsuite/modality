package cn.slainc.webhook.api;

import cn.slainc.webhook.sender.WebhookSendRequest;
import cn.slainc.webhook.sender.WebhookSendResult;

public interface WebhookSender {

    WebhookSendResult send(WebhookSendRequest request);
}
