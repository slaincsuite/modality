package cn.slainc.tenant.context;

import cn.slainc.core.result.Result;
import cn.slainc.core.result.ResultCode;
import cn.slainc.utils.json.JsonUtils;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class TenantRejections {

    private static final Logger log = LoggerFactory.getLogger(TenantRejections.class);

    private TenantRejections() {
    }

    public static void write(ServletResponse response, int httpStatus, int code, String message) throws IOException {
        if (!(response instanceof HttpServletResponse httpResponse)) {
            log.warn("响应对象不是 HTTP 响应，无法返回统一报文：{}", message);
            return;
        }
        httpResponse.setStatus(httpStatus);
        httpResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        httpResponse.setContentType("application/json;charset=UTF-8");
        httpResponse.getWriter().write(body(code, message));
        httpResponse.getWriter().flush();
        log.warn("拒绝请求：{}", message);
    }

    public static void forbidden(ServletResponse response, String message) throws IOException {
        write(response, ResultCode.FORBIDDEN.httpStatus(), ResultCode.FORBIDDEN.code(), message);
    }

    private static String body(int code, String message) {
        try {
            return JsonUtils.toJson(Result.fail(code, message));
        } catch (RuntimeException exception) {
            // JSON 库缺失时退化为手写报文，仍返回统一 Result 结构
            return "{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null,\"timestamp\":"
                    + System.currentTimeMillis() + "}";
        }
    }
}
