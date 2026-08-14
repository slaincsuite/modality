package cn.slainc.core.constant;

import java.time.ZoneId;

public final class SystemConstants {

    public static final Long SUPER_ADMIN_ID = 1L;
    public static final Long SYSTEM_TENANT_ID = 1L;
    public static final Long SYSTEM_ROLE_ID = 1L;
    public static final String SYSTEM_USER = "system";
    public static final String DEFAULT_PASSWORD = "123456";

    public static final Integer NOT_DELETED = 0;
    public static final Integer DELETED = 1;
    public static final Integer ENABLED = 1;
    public static final Integer DISABLED = 0;

    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String TENANT_ID_MDC_KEY = "tenantId";
    public static final String USER_ID_MDC_KEY = "userId";

    public static final String DEFAULT_CHARSET = "UTF-8";
    public static final String DEFAULT_ZONE_NAME = "Asia/Shanghai";
    public static final ZoneId DEFAULT_ZONE = ZoneId.of(DEFAULT_ZONE_NAME);
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    private SystemConstants() {
    }
}
