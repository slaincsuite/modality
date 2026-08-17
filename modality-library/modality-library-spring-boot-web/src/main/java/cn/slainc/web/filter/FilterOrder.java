package cn.slainc.web.filter;

public final class FilterOrder {

    public static final int TRACE = -10000;
    public static final int CORS = -9000;
    public static final int SECURITY_HEADERS = -8000;
    public static final int REQUEST_LOG = -7000;
    public static final int SA_TOKEN = -6000;
    public static final int IDEMPOTENT = -5000;
    public static final int RATE_LIMIT = -4000;
    public static final int SIGN = -3000;
    public static final int COMPRESSION = -2000;

    private FilterOrder() {
    }
}
