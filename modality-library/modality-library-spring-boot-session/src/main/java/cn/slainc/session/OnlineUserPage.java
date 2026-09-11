package cn.slainc.session;

import cn.slainc.core.result.PageResult;

public record OnlineUserPage(PageResult<SessionInfo> page, boolean truncated, String message, int scanned,
                             int maxScan) {

    public long total() {
        return page == null ? 0L : page.total();
    }

    public boolean isEmpty() {
        return page == null || page.isEmpty();
    }
}
