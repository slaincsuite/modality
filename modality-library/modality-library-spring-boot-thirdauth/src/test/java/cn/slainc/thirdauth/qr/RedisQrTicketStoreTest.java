package cn.slainc.thirdauth.qr;

import cn.slainc.thirdauth.ThirdAuthTestApplication;
import cn.slainc.thirdauth.ThirdPartyPlatform;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = ThirdAuthTestApplication.class)
class RedisQrTicketStoreTest {

    @Autowired
    private QrTicketStore ticketStore;

    @Test
    void consumeOnlyTakesExpectedStatusOnce() {
        ticketStore.save(ticket("redis-consume-waiting", QrTicket.Status.WAITING), 60);
        assertTrue(ticketStore.consume("redis-consume-waiting", QrTicket.Status.CONFIRMED).isEmpty(),
                "状态不匹配时不可消费");
        assertTrue(ticketStore.find("redis-consume-waiting").isPresent(), "状态不匹配不应删除票据");

        ticketStore.save(ticket("redis-consume-confirmed", QrTicket.Status.CONFIRMED), 60);
        Optional<QrTicket> taken = ticketStore.consume("redis-consume-confirmed", QrTicket.Status.CONFIRMED);
        assertTrue(taken.isPresent());
        assertEquals("redis-consume-confirmed", taken.orElseThrow().getTicketId());
        assertEquals(ThirdPartyPlatform.FEISHU, taken.orElseThrow().getPlatform());

        assertTrue(ticketStore.consume("redis-consume-confirmed", QrTicket.Status.CONFIRMED).isEmpty(),
                "同一张票据只能被消费一次");
        assertFalse(ticketStore.find("redis-consume-confirmed").isPresent());
    }

    private QrTicket ticket(String ticketId, QrTicket.Status status) {
        QrTicket ticket = new QrTicket();
        ticket.setTicketId(ticketId);
        ticket.setPlatform(ThirdPartyPlatform.FEISHU);
        ticket.setStatus(status.name());
        ticket.setCreatedAt(System.currentTimeMillis());
        ticket.setExpireAt(System.currentTimeMillis() + 60_000);
        return ticket;
    }
}
