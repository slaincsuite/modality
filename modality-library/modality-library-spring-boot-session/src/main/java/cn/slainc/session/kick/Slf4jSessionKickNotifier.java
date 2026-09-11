package cn.slainc.session.kick;

import cn.slainc.session.SessionKickNotice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jSessionKickNotifier implements SessionKickNotifier {

    private static final Logger log = LoggerFactory.getLogger(Slf4jSessionKickNotifier.class);

    @Override
    public void notify(SessionKickNotice notice) {
        log.warn("会话下线通知：账号体系={}，账号={}，设备类型={}，原因={}，提示={}", notice.loginType(), notice.loginId(),
                notice.deviceType() == null ? "-" : notice.deviceType(), notice.reason(), notice.message());
    }

    @Override
    public String channel() {
        return "slf4j";
    }
}
