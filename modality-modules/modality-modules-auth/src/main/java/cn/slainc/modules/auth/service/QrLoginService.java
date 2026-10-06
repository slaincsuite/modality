package cn.slainc.modules.auth.service;
import cn.slainc.modules.auth.qrcode.QrLoginStatus;
import cn.slainc.modules.auth.qrcode.QrLoginTicket;
import cn.slainc.modules.auth.store.AuthTicketStore;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.auth.config.AuthProperties;
import cn.slainc.modules.auth.service.AuthSessionService;
import cn.slainc.security.login.LoginResult;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.PasswordAccount;
import cn.slainc.web.websocket.WebSocketPusher;
import org.springframework.beans.factory.ObjectProvider;

import java.security.SecureRandom;
import java.util.Map;

public class QrLoginService {

    public static final String PUSH_TOPIC_PREFIX = "auth-qr-login:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthTicketStore<QrLoginTicket> store;

    private final QrImageService imageService;

    private final AuthProperties properties;

    private final AuthSessionService sessionService;

    private final ObjectProvider<AccountAuthProvider> authProvider;

    private final WebSocketPusher pusher;

    public QrLoginService(AuthTicketStore<QrLoginTicket> store, QrImageService imageService,
                          AuthProperties properties, AuthSessionService sessionService,
                          ObjectProvider<AccountAuthProvider> authProvider,
                          WebSocketPusher pusher) {
        this.store = store;
        this.imageService = imageService;
        this.properties = properties;
        this.sessionService = sessionService;
        this.authProvider = authProvider;
        this.pusher = pusher;
    }

    public record QrTicketBundle(String ticketId, String content, String imageBase64, long ttlSeconds,
                                 long expireAt) {
    }

    public record QrStatusView(String ticketId, String status, String statusLabel, String scannedUsername,
                               long expireAt) {

        public static QrStatusView of(QrLoginTicket ticket) {
            return new QrStatusView(ticket.getTicketId(), ticket.getStatus().name(), ticket.getStatus().label(),
                    ticket.getUsername(), ticket.getExpireAt());
        }

        public static QrStatusView expired(String ticketId) {
            return new QrStatusView(ticketId, QrLoginStatus.EXPIRED.name(), QrLoginStatus.EXPIRED.label(), null, 0);
        }
    }

    public QrTicketBundle create() {
        QrLoginTicket ticket = QrLoginTicket.waiting(randomTicketId(), properties.getQr().getTtlSeconds());
        store.save(ticket, properties.getQr().getTtlSeconds());
        String content = properties.getQr().getContentPrefix() + ticket.getTicketId();
        return new QrTicketBundle(ticket.getTicketId(), content, imageService.renderBase64(content),
                properties.getQr().getTtlSeconds(), ticket.getExpireAt());
    }

    public QrStatusView status(String ticketId) {
        return store.find(ticketId).map(QrStatusView::of).orElseGet(() -> QrStatusView.expired(ticketId));
    }

    public void scan(String ticketId, LoginType type, Object loginId) {
        QrLoginTicket ticket = requireActive(ticketId);
        if (ticket.getStatus() != QrLoginStatus.WAITING) {
            throw new BizException("二维码已被使用或已取消，请刷新后重试");
        }
        ticket.setStatus(QrLoginStatus.SCANNED);
        ticket.setLoginId(asLong(loginId));
        ticket.setUsername(usernameOf(type, loginId));
        saveRemaining(ticket);
        push(ticket);
    }

    public void confirm(String ticketId, LoginType type, Object loginId) {
        QrLoginTicket ticket = requireActive(ticketId);
        if (ticket.getStatus() != QrLoginStatus.SCANNED) {
            throw new BizException("二维码状态已变化，请刷新后重试");
        }
        if (!java.util.Objects.equals(ticket.getLoginId(), asLong(loginId))) {
            throw new BizException("仅扫码用户本人可确认登录");
        }
        ticket.setStatus(QrLoginStatus.CONFIRMED);
        saveRemaining(ticket);
        push(ticket);
    }

    public void cancel(String ticketId, LoginType type, Object loginId) {
        QrLoginTicket ticket = requireActive(ticketId);
        if (ticket.getStatus() != QrLoginStatus.WAITING && ticket.getStatus() != QrLoginStatus.SCANNED) {
            return;
        }
        if (!java.util.Objects.equals(ticket.getLoginId(), asLong(loginId))) {
            throw new BizException("仅扫码用户本人可取消登录");
        }
        ticket.setStatus(QrLoginStatus.CANCELLED);
        saveRemaining(ticket);
        push(ticket);
    }

    public LoginResult exchange(String ticketId, LoginType type, String deviceType, String deviceId) {
        QrLoginTicket ticket = requireActive(ticketId);
        if (ticket.getStatus() != QrLoginStatus.CONFIRMED) {
            throw new BizException("二维码尚未确认，请先在移动端确认登录");
        }
        // 原子消费确认码，并发下只有一次交换能成功
        if (store.consume(ticketId).isEmpty()) {
            throw new BizException("二维码已被使用，请刷新后重试");
        }
        return sessionService.establish(type, ticket.getLoginId(), deviceType, deviceId);
    }

    public QrTicketBundle refresh(String oldTicketId) {
        store.delete(oldTicketId);
        return create();
    }

    private QrLoginTicket requireActive(String ticketId) {
        QrLoginTicket ticket = store.find(ticketId).orElse(null);
        if (ticket == null || ticket.expired(System.currentTimeMillis())) {
            throw new BizException("二维码已过期或不存在，请刷新后重试");
        }
        return ticket;
    }

    private void saveRemaining(QrLoginTicket ticket) {
        store.save(ticket, ticket.remainingSeconds(System.currentTimeMillis()));
    }

    private void push(QrLoginTicket ticket) {
        pusher.broadcast(PUSH_TOPIC_PREFIX + ticket.getTicketId(),
                Map.of("ticketId", ticket.getTicketId(), "status", ticket.getStatus().name(),
                        "statusLabel", ticket.getStatus().label(), "at", System.currentTimeMillis()));
    }

    private String usernameOf(LoginType type, Object loginId) {
        AccountAuthProvider provider = authProvider.getIfAvailable();
        if (provider == null) {
            return null;
        }
        Long userId = asLong(loginId);
        PasswordAccount account = userId == null ? null : provider.findAccountByUserId(userId, type);
        return account == null ? null : account.username();
    }

    private Long asLong(Object loginId) {
        if (loginId instanceof Number number) {
            return number.longValue();
        }
        if (loginId != null) {
            try {
                return Long.parseLong(loginId.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String randomTicketId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(32);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
