package cn.slainc.core.context;

public final class RequestContext {

    private static final ThreadLocal<RequestInfo> HOLDER = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void set(RequestInfo requestInfo) {
        if (requestInfo == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(requestInfo);
        }
    }

    public static RequestInfo get() {
        return HOLDER.get();
    }

    public static String traceId() {
        RequestInfo info = HOLDER.get();
        return info == null ? null : info.traceId();
    }

    public static String clientIp() {
        RequestInfo info = HOLDER.get();
        return info == null ? null : info.clientIp();
    }

    public static String userAgent() {
        RequestInfo info = HOLDER.get();
        return info == null ? null : info.userAgent();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record RequestInfo(String traceId, String clientIp, String userAgent, String method, String requestUri) {

        public static RequestInfo of(String traceId) {
            return new RequestInfo(traceId, null, null, null, null);
        }
    }
}
