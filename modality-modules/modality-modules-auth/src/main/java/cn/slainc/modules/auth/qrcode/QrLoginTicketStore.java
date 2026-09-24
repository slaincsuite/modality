package cn.slainc.modules.auth.qrcode;

import java.util.Optional;

public interface QrLoginTicketStore {

    void save(QrLoginTicket ticket, long ttlSeconds);

    Optional<QrLoginTicket> find(String ticketId);

    void delete(String ticketId);
}
