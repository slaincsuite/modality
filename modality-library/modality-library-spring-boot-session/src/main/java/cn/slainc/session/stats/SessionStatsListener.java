package cn.slainc.session.stats;

public interface SessionStatsListener {

    void onSnapshot(SessionStats stats);
}
