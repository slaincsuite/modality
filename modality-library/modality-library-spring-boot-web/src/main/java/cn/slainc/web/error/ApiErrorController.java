package cn.slainc.web.error;

import cn.slainc.core.result.Result;
import cn.slainc.core.result.ResultCode;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<Result<Void>> handleError(HttpServletRequest request) {
        int status = resolveStatus(request);
        ResultCode resultCode = resolveCode(status);
        return ResponseEntity.status(status).body(Result.fail(resultCode.code(), resolveMessage(request, resultCode)));
    }

    private int resolveStatus(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        return attribute instanceof Integer value ? value : 500;
    }

    private ResultCode resolveCode(int status) {
        return switch (status) {
            case 400 -> ResultCode.BAD_REQUEST;
            case 401 -> ResultCode.UNAUTHORIZED;
            case 403 -> ResultCode.FORBIDDEN;
            case 404 -> ResultCode.NOT_FOUND;
            case 405 -> ResultCode.METHOD_NOT_ALLOWED;
            case 408 -> ResultCode.REQUEST_TIMEOUT;
            case 409 -> ResultCode.CONFLICT;
            case 413 -> ResultCode.PAYLOAD_TOO_LARGE;
            case 415 -> ResultCode.UNSUPPORTED_MEDIA_TYPE;
            case 429 -> ResultCode.TOO_MANY_REQUESTS;
            case 502 -> ResultCode.THIRD_PARTY_ERROR;
            case 503 -> ResultCode.SERVICE_UNAVAILABLE;
            default -> ResultCode.INTERNAL_SERVER_ERROR;
        };
    }

    private String resolveMessage(HttpServletRequest request, ResultCode resultCode) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        if (attribute instanceof String text && !text.isBlank()) {
            return text;
        }
        return resultCode.message();
    }
}
