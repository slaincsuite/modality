package cn.slainc.modules.auth.third;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryThirdBindTicketStore implements ThirdBindTicketStore {

    private final Map<String, ThirdBindTicket> tickets = new ConcurrentHashMap<>();

    @Override
    public void save(ThirdBindTicket ticket, long ttlSeconds) {
        tickets.put(ticket.getTicketId(), ticket);
    }

    @Override
    public Optional<ThirdBindTicket> find(String ticketId) {
        ThirdBindTicket ticket = tickets.get(ticketId);
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
