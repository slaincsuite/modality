package cn.slainc.services.livemonitor;

// 直播监控表名、状态与权限码：控制器和服务统一引用，避免散落字面量
public final class LiveMonitorKeys {

    public static final String STREAM_TABLE = "t_live_stream";

    public static final String STATUS_UP = "UP";

    public static final String STATUS_DOWN = "DOWN";

    public static final String STATUS_UNKNOWN = "UNKNOWN";

    public static final String PERMISSION_STREAM_QUERY = "live:stream:query";

    public static final String PERMISSION_STREAM_CREATE = "live:stream:create";

    public static final String PERMISSION_STREAM_UPDATE = "live:stream:update";

    public static final String PERMISSION_STREAM_DELETE = "live:stream:delete";

    public static final String PERMISSION_STREAM_CHECK = "live:stream:check";

    private LiveMonitorKeys() {
    }
}
