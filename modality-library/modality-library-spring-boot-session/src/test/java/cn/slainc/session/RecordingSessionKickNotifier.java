package cn.slainc.session;

import cn.slainc.session.kick.SessionKickNotifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingSessionKickNotifier implements SessionKickNotifier {

    private final List<SessionKickNotice> notices = new CopyOnWriteArrayList<>();

    @Override
    public void notify(SessionKickNotice notice) {
        notices.add(notice);
    }

    @Override
    public String channel() {
        return "recording";
    }

    public List<SessionKickNotice> notices() {
        return new ArrayList<>(notices);
    }

    public void clear() {
        notices.clear();
    }
}
