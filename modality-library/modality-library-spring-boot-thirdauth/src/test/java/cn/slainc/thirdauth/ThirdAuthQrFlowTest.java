package cn.slainc.thirdauth;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThirdAuthQrFlowTest {

    private ThirdAuthService service;

    private MemoryQrTicketStore qrTicketStore;

    @BeforeEach
    void setUp() {
        FakeProvider provider = new FakeProvider(ThirdPartyPlatform.WECHAT);
        ThirdAuthProperties properties = new ThirdAuthProperties();
        properties.setQrTicketTtlSeconds(300);
        ThirdAuthProperties.Platform config = new ThirdAuthProperties.Platform();
        config.setEnabled(true);
        config.setAppKey("wx_app");
        config.setAppSecret("secret");
        config.setRedirectUri("https://cb.example.com/thirdauth/callback/wechat");
        properties.getPlatform().put(ThirdPartyPlatform.WECHAT.code(), config);
        qrTicketStore = new MemoryQrTicketStore();
        service = new ThirdAuthService(new ThirdAuthConfigRegistry(properties, null), List.of(provider),
                new MemoryStateStore(), qrTicketStore,
                new InMemoryThirdAccountBindingStore(), properties);
    }

    @Test
    void qrFullFlowWaitingScanConfirmLoop() {
        ThirdAuthService.ThirdAuthLoginContext context = service.beginQrLogin(ThirdPartyPlatform.WECHAT);
        String ticketId = context.state();
        assertTrue(context.authorizeUrl().contains(ticketId));
        assertEquals(QrTicket.Status.WAITING.name(), service.pollQr(ticketId).getStatus());

        service.markScanned(ticketId);
        assertEquals(QrTicket.Status.SCANNED.name(), service.pollQr(ticketId).getStatus());

        ThirdAuthService.ThirdAuthLoginResult result = service.handleCallback("wechat",
                Map.of("code", "code-qr", "state", ticketId));
        assertTrue(result.needBinding());

        QrTicket confirmed = service.pollQr(ticketId);
        assertEquals(QrTicket.Status.CONFIRMED.name(), confirmed.getStatus());
        assertNotNull(confirmed.getPayload());
        assertTrue(confirmed.getPayload().contains(result.user().getOpenId()));
    }

    @Test
    void qrCallbackWritesBindingIntoTicket() {
        ThirdAuthService.ThirdAuthLoginContext context = service.beginQrLogin(ThirdPartyPlatform.WECHAT);
        String ticketId = context.state();
        ThirdAuthService.ThirdAuthLoginResult first = service.handleCallback("wechat",
                Map.of("code", "code-1", "state", ticketId));
        service.bind(first.user(), "6001");

        ThirdAuthService.ThirdAuthLoginContext second = service.beginQrLogin(ThirdPartyPlatform.WECHAT);
        ThirdAuthService.ThirdAuthLoginResult bound = service.handleCallback("wechat",
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
    void expiredTicketReturnsExpiredStatus() {
        QrTicket expired = new QrTicket();
        expired.setTicketId("expired-ticket");
        expired.setExpireAt(System.currentTimeMillis() - 1000);
        qrTicketStore.save(expired, 1);
        assertEquals(QrTicket.Status.EXPIRED.name(), service.pollQr("expired-ticket").getStatus());
    }
}
