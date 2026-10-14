package cn.slainc.webhook.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookEventMatcherTest {

    @Test
    void shouldMatchTheWildcard() {
        assertThat(WebhookEventMatcher.matches("*", WebhookEventTypes.API_KEY_CREATED)).isTrue();
        assertThat(WebhookEventMatcher.matches("open.webhook.subscription-created,*",
                WebhookEventTypes.API_KEY_CREATED)).isTrue();
    }

    @Test
    void shouldMatchAnExactEventInsideACommaSeparatedList() {
        String events = "open.apikey.created, open.apikey.rotated";

        assertThat(WebhookEventMatcher.matches(events, WebhookEventTypes.API_KEY_CREATED)).isTrue();
        assertThat(WebhookEventMatcher.matches(events, WebhookEventTypes.API_KEY_ROTATED)).isTrue();
        assertThat(WebhookEventMatcher.matches(events, WebhookEventTypes.API_KEY_REMOVED)).isFalse();
    }

    @Test
    void shouldMatchPrefixWildcards() {
        assertThat(WebhookEventMatcher.matches("open.apikey.*", WebhookEventTypes.API_KEY_CREATED)).isTrue();
        assertThat(WebhookEventMatcher.matches("open.*", WebhookEventTypes.WEBHOOK_SUBSCRIPTION_UPDATED)).isTrue();
        assertThat(WebhookEventMatcher.matches("upms.*", WebhookEventTypes.API_KEY_CREATED)).isFalse();
    }

    @Test
    void shouldNotMatchWithoutASubscriptionOrAnEvent() {
        assertThat(WebhookEventMatcher.matches(null, WebhookEventTypes.API_KEY_CREATED)).isFalse();
        assertThat(WebhookEventMatcher.matches("  ", WebhookEventTypes.API_KEY_CREATED)).isFalse();
        assertThat(WebhookEventMatcher.matches("*", null)).isFalse();
    }

    @Test
    void shouldExposeTheBuiltInEvents() {
        assertThat(WebhookEventTypes.all()).extracting(WebhookEventTypes.EventType::name)
                .contains(WebhookEventTypes.API_KEY_CREATED, WebhookEventTypes.WEBHOOK_SUBSCRIPTION_REMOVED);
    }
}
