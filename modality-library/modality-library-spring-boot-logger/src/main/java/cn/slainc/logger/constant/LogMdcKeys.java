package cn.slainc.logger.constant;

import cn.slainc.core.constant.SystemConstants;

import java.util.List;

public final class LogMdcKeys {

    public static final String SPAN_ID = "spanId";
    public static final String PARENT_SPAN_ID = "parentSpanId";
    public static final String LOGIN_ID = "loginId";
    public static final String USERNAME = "username";
    public static final String CLIENT_IP = "clientIp";
    public static final String USER_AGENT = "userAgent";
    public static final String DEVICE_ID = "deviceId";
    public static final String REQUEST_PATH = "requestPath";
    public static final String HTTP_METHOD = "httpMethod";
    public static final String AUDIT_CATEGORY = "auditCategory";
    public static final String AUDIT_LEVEL = "auditLevel";
    public static final String SAMPLING_EXEMPT = "samplingExempt";

    public static final List<String> RESERVED = List.of(
            SystemConstants.TRACE_ID_MDC_KEY, SystemConstants.TENANT_ID_MDC_KEY, SystemConstants.USER_ID_MDC_KEY,
            SPAN_ID, PARENT_SPAN_ID, LOGIN_ID, USERNAME, CLIENT_IP, USER_AGENT, DEVICE_ID,
            REQUEST_PATH, HTTP_METHOD, AUDIT_CATEGORY, AUDIT_LEVEL, SAMPLING_EXEMPT);

    public static boolean isReserved(String key) {
        return key != null && RESERVED.contains(key);
    }

    private LogMdcKeys() {
    }
}
