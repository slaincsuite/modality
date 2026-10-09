package cn.slainc.core.constant;

import java.time.ZoneId;

public final class SystemConstants {

    public static final String SYSTEM_USER = "system";

    public static final Integer NOT_DELETED = 0;

    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String TENANT_ID_MDC_KEY = "tenantId";
    public static final String USER_ID_MDC_KEY = "userId";

    public static final String DEFAULT_ZONE_NAME = "Asia/Shanghai";
    public static final ZoneId DEFAULT_ZONE = ZoneId.of(DEFAULT_ZONE_NAME);

    private SystemConstants() {
    }
}
