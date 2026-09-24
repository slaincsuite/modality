package cn.slainc.modules.auth.qrcode;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryQrLoginTicketStore implements QrLoginTicketStore {

    private final Map<String, QrLoginTicket> tickets = new ConcurrentHashMap<>();

    @Override
    public void save(QrLoginTicket ticket, long ttlSeconds) {
        tickets.put(ticket.getTicketId(), ticket);
    }

    @Override
    public Optional<QrLoginTicket> find(String ticketId) {
        QrLoginTicket ticket = tickets.get(ticketId);
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
    public void delete(String ticketId) {
        tickets.remove(ticketId);
    }
}
