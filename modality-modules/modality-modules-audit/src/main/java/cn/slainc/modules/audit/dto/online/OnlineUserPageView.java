package cn.slainc.modules.audit.dto.online;

import cn.slainc.core.result.PageResult;

public record OnlineUserPageView(PageResult<OnlineSessionView> page, boolean truncated, String message, int scanned,
                                 int maxScan) {
}
