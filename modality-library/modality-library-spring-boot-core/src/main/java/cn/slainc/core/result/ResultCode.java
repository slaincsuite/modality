package cn.slainc.core.result;

public enum ResultCode implements ErrorCode {

    SUCCESS(200, "成功"),
    BAD_REQUEST(400000, "请求参数错误"),
    PARAM_MISSING(400001, "缺少必要参数"),
    PARAM_TYPE_MISMATCH(400002, "参数类型不匹配"),
    PARAM_BIND_ERROR(400003, "参数绑定失败"),
    UNAUTHORIZED(401000, "未认证或登录已过期"),
    FORBIDDEN(403000, "没有访问权限"),
    NOT_FOUND(404000, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405000, "请求方法不支持"),
    REQUEST_TIMEOUT(408000, "请求超时"),
    CONFLICT(409000, "数据已存在或被占用"),
    PAYLOAD_TOO_LARGE(413000, "请求体过大"),
    UNSUPPORTED_MEDIA_TYPE(415000, "不支持的媒体类型"),
    TOO_MANY_REQUESTS(429000, "请求过于频繁"),
    BUSINESS_ERROR(400100, "业务处理失败"),
    DATA_NOT_FOUND(400101, "数据不存在"),
    DATA_DUPLICATE(400102, "数据重复"),
    DATA_STATE_INVALID(400103, "数据状态不允许该操作"),
    INTERNAL_SERVER_ERROR(500000, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503000, "服务暂不可用"),
    THIRD_PARTY_ERROR(502000, "第三方服务调用失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public static ResultCode of(int code) {
        for (ResultCode resultCode : values()) {
            if (resultCode.code == code) {
                return resultCode;
            }
        }
        return null;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
