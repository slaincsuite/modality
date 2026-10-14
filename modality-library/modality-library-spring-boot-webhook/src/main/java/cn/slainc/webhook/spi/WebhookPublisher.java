package cn.slainc.webhook.spi;

public interface WebhookPublisher {

    void publish(String eventType, Object payload);
}
