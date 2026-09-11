package cn.slainc.session.kick;

import cn.slainc.session.SessionKickNotice;
import cn.slainc.web.websocket.WebSocketBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketSessionKickNotifier implements SessionKickNotifier {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionKickNotifier.class);

    private final WebSocketBroadcaster broadcaster;

    public WebSocketSessionKickNotifier(WebSocketBroadcaster broadcaster) {
        if (broadcaster == null) {
            throw new IllegalArgumentException("WebSocketBroadcaster 不能为空");
        }
        this.broadcaster = broadcaster;
    }

    @Override
    public void notify(SessionKickNotice notice) {
        try {
            broadcaster.sendToUser(String.valueOf(notice.loginId()), notice.toJson());
            log.info("已通过 WebSocket 推送会话下线通知：账号体系={}，账号={}，原因={}", notice.loginType(), notice.loginId(),
                    notice.reason());
        } catch (Exception e) {
            log.warn("WebSocket 推送会话下线通知失败：{}", e.getMessage());
        }
    }

    @Override
    public String channel() {
        return "websocket";
    }
}
