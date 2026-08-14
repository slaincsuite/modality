package cn.slainc.core.context;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ContextSnapshot {

    private final UserContext.UserInfo userInfo;
    private final Long tenantId;
    private final RequestContext.RequestInfo requestInfo;

    private ContextSnapshot(UserContext.UserInfo userInfo, Long tenantId, RequestContext.RequestInfo requestInfo) {
        this.userInfo = userInfo;
        this.tenantId = tenantId;
        this.requestInfo = requestInfo;
    }

    public static ContextSnapshot capture() {
        return new ContextSnapshot(UserContext.get(), TenantContext.get(), RequestContext.get());
    }

    public void restore() {
        UserContext.set(userInfo);
        TenantContext.set(tenantId);
        RequestContext.set(requestInfo);
    }

    public static void clearAll() {
        UserContext.clear();
        TenantContext.clear();
        RequestContext.clear();
    }

    public static void restoreAll(ContextSnapshot snapshot) {
        if (snapshot == null) {
            clearAll();
        } else {
            snapshot.restore();
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("userId", userInfo == null ? null : userInfo.userId());
        values.put("username", userInfo == null ? null : userInfo.username());
        values.put("tenantId", tenantId);
        values.put("traceId", requestInfo == null ? null : requestInfo.traceId());
        return values;
    }
}
