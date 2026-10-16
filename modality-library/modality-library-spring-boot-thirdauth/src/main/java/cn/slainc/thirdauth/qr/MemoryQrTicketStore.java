package cn.slainc.thirdauth.qr;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryQrTicketStore implements QrTicketStore {

    private static final int SWEEP_THRESHOLD = 128;

    private final Map<String, QrTicket> tickets = new ConcurrentHashMap<>();

    @Override
    public void save(QrTicket ticket, long ttlSeconds) {
        if (tickets.size() >= SWEEP_THRESHOLD) {
            evictExpired();
        }
        tickets.put(ticket.getTicketId(), ticket);
    }

    // 到阈值才全量清理，避免每次保存都扫描；被遗弃且无人轮询的票据不再无限堆积
    private void evictExpired() {
        long now = System.currentTimeMillis();
        tickets.values().removeIf(ticket -> ticket.expired(now));
    }

    @Override
    public Optional<QrTicket> find(String ticketId) {
        QrTicket ticket = tickets.get(ticketId);
        if (ticket == null) {
            return Optional.empty();
        }
        if (ticket.expired(System.currentTimeMillis())) {
            tickets.remove(ticketId);
            return Optional.empty();
        }
        return Optional.of(ticket);
    }

    @Override
    public Optional<QrTicket> consume(String ticketId, QrTicket.Status expected) {
        QrTicket ticket = tickets.get(ticketId);
        if (ticket == null || ticket.expired(System.currentTimeMillis())
                || !expected.name().equals(ticket.getStatus())) {
            return Optional.empty();
        }
        return tickets.remove(ticketId, ticket) ? Optional.of(ticket) : Optional.empty();
    }
}
