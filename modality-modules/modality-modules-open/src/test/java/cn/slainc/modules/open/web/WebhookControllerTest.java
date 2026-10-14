package cn.slainc.modules.open.web;

import cn.slainc.core.result.PageResult;
import cn.slainc.logger.audit.AuditLog;
import cn.slainc.modules.open.OpenKeys;
import cn.slainc.modules.open.dto.WebhookSubscriptionRequest;
import cn.slainc.modules.open.service.WebhookDeliveryAdminService;
import cn.slainc.modules.open.service.WebhookSubscriptionAdminService;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.webhook.support.WebhookEventTypes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookControllerTest {

    private final WebhookSubscriptionAdminService subscriptionService =
            mock(WebhookSubscriptionAdminService.class);

    private final WebhookDeliveryAdminService deliveryService =
            mock(WebhookDeliveryAdminService.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new WebhookController(subscriptionService, deliveryService)).build();

    @Test
    void shouldExposeTheSubscriptionPage() throws Exception {
        when(subscriptionService.page(any(), any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(PageResult.of(List.of(subscription()), 1L, 1L, 10L));

        mockMvc.perform(get("/open/webhook/page").param("name", "demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].name").value("demo"));
    }

    @Test
    void shouldCreateASubscription() throws Exception {
        when(subscriptionService.create(any())).thenReturn(subscription());

        mockMvc.perform(post("/open/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"demo\",\"url\":\"https://example.com/hook\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));

        ArgumentCaptor<WebhookSubscriptionRequest> captor =
                ArgumentCaptor.forClass(WebhookSubscriptionRequest.class);
        verify(subscriptionService).create(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("demo");
        assertThat(captor.getValue().url()).isEqualTo("https://example.com/hook");
    }

    @Test
    void shouldUpdateThePathIdentifier() throws Exception {
        when(subscriptionService.update(any(), any())).thenReturn(subscription());

        mockMvc.perform(put("/open/webhook/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"demo\",\"url\":\"https://example.com/hook\"}"))
                .andExpect(status().isOk());

        verify(subscriptionService).update(eq(5L), any(WebhookSubscriptionRequest.class));
    }

    @Test
    void shouldDeleteCommaSeparatedSubscriptions() throws Exception {
        when(subscriptionService.remove(List.of(5L, 6L))).thenReturn(2);

        mockMvc.perform(delete("/open/webhook/5,6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(2));
    }

    @Test
    void shouldPageTheDeliveriesOfASubscription() throws Exception {
        Map<String, Object> delivery = new LinkedHashMap<>();
        delivery.put("id", 9L);
        delivery.put("status", "FAILED");
        when(deliveryService.page(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(PageResult.of(List.of(delivery), 1L, 1L, 10L));

        mockMvc.perform(get("/open/webhook/5/deliveries").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].status").value("FAILED"));

        verify(deliveryService).page(eq(5L), org.mockito.ArgumentMatchers.isNull(), eq("FAILED"),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldRetryADelivery() throws Exception {
        when(deliveryService.retry(9L)).thenReturn(true);

        mockMvc.perform(post("/open/webhook/deliveries/9/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
    }

    @Test
    void shouldExposeTheEventTypes() throws Exception {
        when(subscriptionService.events()).thenReturn(WebhookEventTypes.all());

        mockMvc.perform(get("/open/webhook/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value(WebhookEventTypes.API_KEY_CREATED));
    }

    @Test
    void shouldMountEveryRouteWithPermissionsAndAuditLogs() throws Exception {
        assertThat(WebhookController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/open/webhook");

        assertPermission("page", new Class<?>[]{String.class, Boolean.class, long.class,
                long.class}, OpenKeys.PERMISSION_WEBHOOK_QUERY);
        assertPermission("detail", new Class<?>[]{Long.class}, OpenKeys.PERMISSION_WEBHOOK_QUERY);
        assertPermission("create", new Class<?>[]{WebhookSubscriptionRequest.class},
                OpenKeys.PERMISSION_WEBHOOK_CREATE);
        assertPermission("update", new Class<?>[]{Long.class, WebhookSubscriptionRequest.class},
                OpenKeys.PERMISSION_WEBHOOK_UPDATE);
        assertPermission("delete", new Class<?>[]{List.class}, OpenKeys.PERMISSION_WEBHOOK_DELETE);
        assertPermission("deliveries", new Class<?>[]{Long.class, String.class, String.class,
                long.class, long.class}, OpenKeys.PERMISSION_WEBHOOK_DELIVERY_QUERY);
        assertPermission("retry", new Class<?>[]{Long.class}, OpenKeys.PERMISSION_WEBHOOK_DELIVERY_RETRY);
        assertPermission("events", new Class<?>[]{}, OpenKeys.PERMISSION_WEBHOOK_QUERY);

        assertThat(WebhookController.class.getMethod("create", WebhookSubscriptionRequest.class)
                .getAnnotation(AuditLog.class)).isNotNull();
        assertThat(WebhookController.class.getMethod("update", Long.class,
                WebhookSubscriptionRequest.class).getAnnotation(AuditLog.class)).isNotNull();
        assertThat(WebhookController.class.getMethod("delete", List.class)
                .getAnnotation(AuditLog.class)).isNotNull();
        assertThat(WebhookController.class.getMethod("retry", Long.class)
                .getAnnotation(AuditLog.class)).isNotNull();
    }

    private void assertPermission(String name, Class<?>[] parameterTypes, String permission)
            throws Exception {
        Method method = WebhookController.class.getMethod(name, parameterTypes);
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.permissions()).containsExactly(permission);
    }

    private static Map<String, Object> subscription() {
        Map<String, Object> subscription = new LinkedHashMap<>();
        subscription.put("id", 5L);
        subscription.put("name", "demo");
        subscription.put("url", "https://example.com/hook");
        subscription.put("secretMasked", "wh*c");
        subscription.put("events", "*");
        subscription.put("enabled", Boolean.TRUE);
        return subscription;
    }
}
