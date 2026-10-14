package cn.slainc.webhook.spi;

import cn.slainc.webhook.sender.WebhookSendRequest;
import cn.slainc.webhook.sender.WebhookSendResult;

public interface WebhookSender {

    WebhookSendResult send(WebhookSendRequest request);
}
