package cn.slainc.thirdauth.qr;

import cn.slainc.thirdauth.ThirdPartyPlatform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryQrTicketStoreTest {

    @Test
    @DisplayName("容量到阈值时清理过期票据，被遗弃的票据不再堆积")
    void evictsExpiredTicketsAtThreshold() {
        MemoryQrTicketStore store = new MemoryQrTicketStore();
        for (int index = 0; index < 128; index++) {
            store.save(expired("abandoned-" + index), 300);
        }
        store.save(waiting("fresh"), 300);

        assertEquals(1, ticketsOf(store).size());
        assertTrue(store.find("fresh").isPresent());
    }

    @Test
    @DisplayName("过期或状态不符时消费失败，命中后票据被移除")
    void consumeRequiresExpectedStatusAndRemovesTicket() {
        MemoryQrTicketStore store = new MemoryQrTicketStore();
        QrTicket waiting = waiting("ticket-1");
        store.save(waiting, 300);

        assertFalse(store.consume("ticket-1", QrTicket.Status.CONFIRMED).isPresent());

        waiting.setStatus(QrTicket.Status.CONFIRMED.name());
        Optional<QrTicket> consumed = store.consume("ticket-1", QrTicket.Status.CONFIRMED);
        assertTrue(consumed.isPresent());
        assertFalse(store.find("ticket-1").isPresent());

        store.save(expired("ticket-2"), 300);
        assertFalse(store.consume("ticket-2", QrTicket.Status.CONFIRMED).isPresent());
    }

    private static QrTicket waiting(String ticketId) {
        QrTicket ticket = new QrTicket();
        ticket.setTicketId(ticketId);
        ticket.setPlatform(ThirdPartyPlatform.FEISHU);
        ticket.setCreatedAt(System.currentTimeMillis());
        ticket.setExpireAt(System.currentTimeMillis() + 300_000L);
        return ticket;
    }

    private static QrTicket expired(String ticketId) {
        QrTicket ticket = waiting(ticketId);
        ticket.setStatus(QrTicket.Status.CONFIRMED.name());
        ticket.setExpireAt(System.currentTimeMillis() - 1_000L);
        return ticket;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, QrTicket> ticketsOf(MemoryQrTicketStore store) {
        return (Map<String, QrTicket>) ReflectionTestUtils.getField(store, "tickets");
    }
}
