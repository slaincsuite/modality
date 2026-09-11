package cn.slainc.session.kick;

import cn.slainc.session.SessionKickNotice;

public interface SessionKickNotifier {

    void notify(SessionKickNotice notice);

    default String channel() {
        return "default";
    }
}
