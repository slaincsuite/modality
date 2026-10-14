package cn.slainc.webhook.spi;

import java.time.LocalDateTime;
import java.util.List;

public interface WebhookDeliveryPort {

    Long createPending(WebhookDeliveryCommand command);

    List<WebhookDeliveryTask> findDue(int limit);

    void markSuccess(Long id, int httpStatus, String responseBody, long costMillis);

    void markFailure(Long id, int attempt, int httpStatus, String responseBody, String errorMessage,
                     LocalDateTime nextRetryAt, boolean exhausted, long costMillis);
}
