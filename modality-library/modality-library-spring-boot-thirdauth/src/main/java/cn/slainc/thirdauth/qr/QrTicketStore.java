package cn.slainc.thirdauth.qr;

import java.util.Optional;

public interface QrTicketStore {

    void save(QrTicket ticket, long ttlSeconds);

    Optional<QrTicket> find(String ticketId);
}
