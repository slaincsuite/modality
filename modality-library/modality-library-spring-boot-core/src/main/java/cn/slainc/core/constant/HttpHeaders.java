package cn.slainc.core.constant;

public final class HttpHeaders {

    public static final String TRACE_ID = "X-Trace-Id";
    public static final String REQUEST_ID = "X-Request-Id";
    public static final String TENANT_ID = "X-Tenant-Id";
    public static final String AUTHORIZATION = "Authorization";
    public static final String API_KEY = "X-Api-Key";
    public static final String SIGN = "X-Sign";
    public static final String TIMESTAMP = "X-Timestamp";
    public static final String NONCE = "X-Nonce";
    public static final String FORWARDED_FOR = "X-Forwarded-For";
    public static final String FORWARDED_PROTO = "X-Forwarded-Proto";
    public static final String REAL_IP = "X-Real-IP";
    public static final String CF_CONNECTING_IP = "CF-Connecting-IP";
    public static final String USER_AGENT = "User-Agent";
    public static final String CONTENT_DISPOSITION = "Content-Disposition";
    public static final String TOKEN_PREFIX = "Bearer ";

    private HttpHeaders() {
    }
}
