package cn.slainc.thirdauth.qr;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryQrTicketStore implements QrTicketStore {

    private final Map<String, QrTicket> tickets = new ConcurrentHashMap<>();

    @Override
    public void save(QrTicket ticket, long ttlSeconds) {
        tickets.put(ticket.getTicketId(), ticket);
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
}
