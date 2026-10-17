package cn.slainc.thirdauth;

import cn.slainc.core.exception.ThirdPartyException;
import cn.slainc.thirdauth.binding.InMemoryThirdAccountBindingStore;
import cn.slainc.thirdauth.config.ThirdAuthConfigRegistry;
import cn.slainc.thirdauth.config.ThirdAuthProperties;
import cn.slainc.thirdauth.qr.MemoryQrTicketStore;
import cn.slainc.thirdauth.qr.QrTicket;
import cn.slainc.thirdauth.state.MemoryStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThirdAuthQrFlowTest {

    private ThirdAuthService service;

    private MemoryQrTicketStore qrTicketStore;

    private InMemoryThirdAccountBindingStore bindingStore;

    @BeforeEach
    void setUp() {
        FakeProvider provider = new FakeProvider(ThirdPartyPlatform.FEISHU);
        ThirdAuthProperties properties = new ThirdAuthProperties();
        properties.setQrTicketTtlSeconds(300);
        ThirdAuthProperties.Platform config = new ThirdAuthProperties.Platform();
        config.setEnabled(true);
        config.setAppKey("cli_app");
        config.setAppSecret("secret");
        config.setRedirectUri("https://cb.example.com/thirdauth/callback/feishu");
        properties.getPlatform().put(ThirdPartyPlatform.FEISHU.code(), config);
        qrTicketStore = new MemoryQrTicketStore();
        bindingStore = new InMemoryThirdAccountBindingStore();
        service = new ThirdAuthService(new ThirdAuthConfigRegistry(properties, null), List.of(provider),
                new MemoryStateStore(), qrTicketStore, bindingStore, properties);
    }

    @Test
    void qrFullFlowWaitingScanConfirmLoop() {
        ThirdAuthService.ThirdAuthLoginContext context = service.beginQrLogin(ThirdPartyPlatform.FEISHU);
        String ticketId = context.state();
        assertTrue(context.authorizeUrl().contains(ticketId));
        assertEquals(QrTicket.Status.WAITING.name(), service.pollQr(ticketId).getStatus());

        service.markScanned(ticketId);
        assertEquals(QrTicket.Status.SCANNED.name(), service.pollQr(ticketId).getStatus());

        ThirdAuthService.ThirdAuthLoginResult result = service.handleCallback("feishu",
                Map.of("code", "code-qr", "state", ticketId));
        assertTrue(result.needBinding());

        QrTicket confirmed = service.pollQr(ticketId);
        assertEquals(QrTicket.Status.CONFIRMED.name(), confirmed.getStatus());
        assertNotNull(confirmed.getPayload());
        assertTrue(confirmed.getPayload().contains(result.user().getOpenId()));
    }

    @Test
    void qrCallbackWritesBindingIntoTicket() {
        ThirdAuthService.ThirdAuthLoginContext context = service.beginQrLogin(ThirdPartyPlatform.FEISHU);
        String ticketId = context.state();
        ThirdAuthService.ThirdAuthLoginResult first = service.handleCallback("feishu",
                Map.of("code", "code-1", "state", ticketId));
        service.bind(first.user(), "6001");

        ThirdAuthService.ThirdAuthLoginContext second = service.beginQrLogin(ThirdPartyPlatform.FEISHU);
        ThirdAuthService.ThirdAuthLoginResult bound = service.handleCallback("feishu",
                Map.of("code", "code-2", "state", second.state()));
        assertTrue(bound.bound());
        QrTicket confirmed = service.pollQr(second.state());
        assertTrue(confirmed.getPayload().contains("6001"));
    }

    @Test
    void missingTicketReturnsExpiredStatus() {
        QrTicket ticket = service.pollQr("not-exist-ticket");
        assertEquals(QrTicket.Status.EXPIRED.name(), ticket.getStatus());
    }

    @Test
    void confirmedTicketConsumedExactlyOnce() {
        ThirdAuthService.ThirdAuthLoginContext context = service.beginQrLogin(ThirdPartyPlatform.FEISHU);
        String ticketId = context.state();
        assertTrue(service.consumeQr(ticketId).isEmpty(), "未确认的票据不可消费");
        assertEquals(QrTicket.Status.WAITING.name(), service.pollQr(ticketId).getStatus(), "状态不符不应消费掉票据");

        ThirdAuthService.ThirdAuthLoginResult confirmed = service.handleCallback("feishu",
                Map.of("code", "code-consume", "state", ticketId));
        ThirdAuthService.ThirdAuthLoginResult consumed = service.consumeQr(ticketId).orElseThrow();
        assertEquals(confirmed.user().getOpenId(), consumed.user().getOpenId());

        assertTrue(service.consumeQr(ticketId).isEmpty(), "已确认票据只能被消费一次");
        assertEquals(QrTicket.Status.EXPIRED.name(), service.pollQr(ticketId).getStatus());
    }

    @Test
    void expiredTicketReturnsExpiredStatus() {
        QrTicket expired = new QrTicket();
        expired.setTicketId("expired-ticket");
        expired.setExpireAt(System.currentTimeMillis() - 1000);
        qrTicketStore.save(expired, 1);
        assertEquals(QrTicket.Status.EXPIRED.name(), service.pollQr("expired-ticket").getStatus());
    }

    @Test
    void bindingQrBindsTargetAndCannotBeConsumedAsLogin() {
        ThirdAuthService.ThirdAuthLoginContext context = service.beginQrBinding(ThirdPartyPlatform.FEISHU, "7001",
                "TENANT_USER");
        String ticketId = context.state();
        QrTicket waiting = service.pollBindingQr(ThirdPartyPlatform.FEISHU, ticketId, "7001", "TENANT_USER")
                .orElseThrow();
        assertEquals(QrTicket.Purpose.BIND.name(), waiting.getPurpose());
        assertEquals("7001", waiting.getTargetUserId());

        service.handleCallback("feishu", Map.of("code", "bind-code", "state", ticketId));

        QrTicket confirmed = service.pollBindingQr(ThirdPartyPlatform.FEISHU, ticketId, "7001", "TENANT_USER")
                .orElseThrow();
        assertEquals(QrTicket.Status.CONFIRMED.name(), confirmed.getStatus());
        assertEquals("7001", service.bindingResult(confirmed).orElseThrow().getUserId());
        assertTrue(service.consumeQr(ticketId).isEmpty(), "绑定二维码不能兑换登录会话");
        assertEquals(1, bindingStore.listByUser("7001").size());
    }

    @Test
    void bindingQrCannotBePolledByAnotherUser() {
        String ticketId = service.beginQrBinding(ThirdPartyPlatform.FEISHU, "7001", "TENANT_USER").state();
        assertThrows(ThirdPartyException.class,
                () -> service.pollBindingQr(ThirdPartyPlatform.FEISHU, ticketId, "7002", "TENANT_USER"));
        assertThrows(ThirdPartyException.class,
                () -> service.pollBindingQr(ThirdPartyPlatform.FEISHU, ticketId, "7001", "PLATFORM_USER"));
    }
}
