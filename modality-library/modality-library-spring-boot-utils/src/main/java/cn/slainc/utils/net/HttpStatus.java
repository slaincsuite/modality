package cn.slainc.utils.net;

public enum HttpStatus {

    CONTINUE(100, "继续"),
    OK(200, "成功"),
    CREATED(201, "已创建"),
    ACCEPTED(202, "已接受"),
    NO_CONTENT(204, "无内容"),
    MOVED_PERMANENTLY(301, "永久重定向"),
    FOUND(302, "临时重定向"),
    NOT_MODIFIED(304, "资源未修改"),
    TEMPORARY_REDIRECT(307, "临时重定向（307）"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    NOT_ACCEPTABLE(406, "不接受的内容类型"),
    REQUEST_TIMEOUT(408, "请求超时"),
    CONFLICT(409, "资源冲突"),
    GONE(410, "资源已删除"),
    PAYLOAD_TOO_LARGE(413, "请求体过大"),
    UNSUPPORTED_MEDIA_TYPE(415, "不支持的媒体类型"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    NOT_IMPLEMENTED(501, "功能未实现"),
    BAD_GATEWAY(502, "网关错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),
    GATEWAY_TIMEOUT(504, "网关超时");

    private final int code;
    private final String reason;

    HttpStatus(int code, String reason) {
        this.code = code;
        this.reason = reason;
    }

    public int code() {
        return code;
    }

    public String reason() {
        return reason;
    }

    public static HttpStatus of(int code) {
        for (HttpStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    public static String reasonOf(int code) {
        HttpStatus status = of(code);
        return status == null ? null : status.reason;
    }

    public static boolean isSuccess(int code) {
        return code >= 200 && code < 300;
    }

    public static boolean isRedirect(int code) {
        return code >= 300 && code < 400;
    }

    public static boolean isClientError(int code) {
        return code >= 400 && code < 500;
    }

    public static boolean isServerError(int code) {
        return code >= 500 && code < 600;
    }
}
