package cn.slainc.web.exception;

import cn.slainc.core.exception.AbstractException;
import cn.slainc.core.exception.ExceptionUtils;
import cn.slainc.core.exception.ThirdPartyException;
import cn.slainc.core.exception.ValidationException;
import cn.slainc.core.result.Result;
import cn.slainc.core.result.ResultCode;
import cn.slainc.web.config.WebExceptionProperties;
import cn.slainc.web.timeout.RequestTimeoutFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final WebExceptionProperties properties;

    public GlobalExceptionHandler(WebExceptionProperties properties) {
        this.properties = properties;
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Result<Void>> handleValidation(ValidationException exception, HttpServletRequest request) {
        List<String> messages = new ArrayList<>(exception.violations());
        if (messages.isEmpty() && exception.getMessage() != null) {
            messages.add(exception.getMessage());
        }
        return validationFailure(messages, request);
    }

    @ExceptionHandler(ThirdPartyException.class)
    public ResponseEntity<Result<Void>> handleThirdParty(ThirdPartyException exception, HttpServletRequest request) {
        log.error("第三方服务异常 [{} {}] service={}", request.getMethod(), request.getRequestURI(),
                exception.service(), exception);
        return build(exception.code(), exception.httpStatus(), exception.getMessage());
    }

    @ExceptionHandler(AbstractException.class)
    public ResponseEntity<Result<Void>> handleAbstract(AbstractException exception, HttpServletRequest request) {
        log.warn("业务异常 [{} {}] code={} message={}", request.getMethod(), request.getRequestURI(),
                exception.code(), exception.getMessage());
        return build(exception.code(), exception.httpStatus(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                     HttpServletRequest request) {
        List<String> messages = new ArrayList<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            messages.add(error.getField() + " " + error.getDefaultMessage());
        }
        for (MessageSourceResolvable error : exception.getBindingResult().getGlobalErrors()) {
            messages.add(error.getDefaultMessage());
        }
        return validationFailure(messages, request);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException exception, HttpServletRequest request) {
        List<String> messages = new ArrayList<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            messages.add(error.getField() + " " + error.getDefaultMessage());
        }
        return validationFailure(messages, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException exception,
                                                                  HttpServletRequest request) {
        List<String> messages = new ArrayList<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            messages.add(violation.getPropertyPath() + " " + violation.getMessage());
        }
        return validationFailure(messages, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleHandlerMethodValidation(HandlerMethodValidationException exception,
                                                                      HttpServletRequest request) {
        List<String> messages = new ArrayList<>();
        for (MessageSourceResolvable error : exception.getAllErrors()) {
            messages.add(error.getDefaultMessage());
        }
        return validationFailure(messages, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(MissingServletRequestParameterException exception,
                                                                HttpServletRequest request) {
        String message = "缺少必要参数 " + exception.getParameterName();
        log.warn("参数异常 [{} {}] {}", request.getMethod(), request.getRequestURI(), message);
        return build(ResultCode.PARAM_MISSING.code(), ResultCode.PARAM_MISSING.httpStatus(), message);
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<Result<Void>> handleRequestBinding(ServletRequestBindingException exception,
                                                             HttpServletRequest request) {
        String message = exception.getMessage() == null ? ResultCode.PARAM_MISSING.message() : exception.getMessage();
        log.warn("参数异常 [{} {}] {}", request.getMethod(), request.getRequestURI(), message);
        return build(ResultCode.PARAM_MISSING.code(), ResultCode.PARAM_MISSING.httpStatus(), message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                           HttpServletRequest request) {
        String message = "参数 " + exception.getName() + " 类型不正确";
        log.warn("参数异常 [{} {}] {}", request.getMethod(), request.getRequestURI(), message);
        return build(ResultCode.PARAM_TYPE_MISMATCH.code(), ResultCode.PARAM_TYPE_MISMATCH.httpStatus(), message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException exception,
                                                          HttpServletRequest request) {
        log.warn("请求体解析失败 [{} {}] {}", request.getMethod(), request.getRequestURI(),
                ExceptionUtils.getMessage(exception));
        return build(ResultCode.PARAM_BIND_ERROR.code(), ResultCode.PARAM_BIND_ERROR.httpStatus(),
                ResultCode.PARAM_BIND_ERROR.message());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception,
                                                                  HttpServletRequest request) {
        String message = "请求方法 " + exception.getMethod() + " 不被支持";
        log.warn("请求异常 [{} {}] {}", request.getMethod(), request.getRequestURI(), message);
        return build(ResultCode.METHOD_NOT_ALLOWED.code(), ResultCode.METHOD_NOT_ALLOWED.httpStatus(), message);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception,
                                                                     HttpServletRequest request) {
        String message = "不支持的媒体类型 " + exception.getContentType();
        log.warn("请求异常 [{} {}] {}", request.getMethod(), request.getRequestURI(), message);
        return build(ResultCode.UNSUPPORTED_MEDIA_TYPE.code(), ResultCode.UNSUPPORTED_MEDIA_TYPE.httpStatus(), message);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> handleNoHandlerFound(NoHandlerFoundException exception,
                                                             HttpServletRequest request) {
        String message = "请求的资源不存在 " + exception.getRequestURL();
        log.warn("请求异常 [{} {}] {}", request.getMethod(), request.getRequestURI(), message);
        return build(ResultCode.NOT_FOUND.code(), ResultCode.NOT_FOUND.httpStatus(), message);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException exception,
                                                              HttpServletRequest request) {
        log.warn("请求的资源不存在 [{} {}]", request.getMethod(), request.getRequestURI());
        return build(ResultCode.NOT_FOUND.code(), ResultCode.NOT_FOUND.httpStatus(), ResultCode.NOT_FOUND.message());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleResponseStatus(ResponseStatusException exception,
                                                             HttpServletRequest request) {
        int status = exception.getStatusCode().value();
        HttpStatus resolved = HttpStatus.resolve(status);
        String message = exception.getReason() == null
                ? (resolved == null ? ResultCode.INTERNAL_SERVER_ERROR.message() : resolved.getReasonPhrase())
                : exception.getReason();
        log.warn("请求被拒绝 [{} {}] status={} reason={}", request.getMethod(), request.getRequestURI(), status, message);
        return build(status * 1000, status, message);
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Result<Void>> handleAsyncTimeout(AsyncRequestTimeoutException exception,
                                                           HttpServletRequest request) {
        log.warn("异步请求处理超时 [{} {}]", request.getMethod(), request.getRequestURI());
        return build(ResultCode.SERVICE_UNAVAILABLE.code(), ResultCode.SERVICE_UNAVAILABLE.httpStatus(),
                "请求处理超时");
    }

    // Spring 在 Callable 异步超时时会先取消并发任务，InterruptedException 可能先于超时异常成为并发结果
    @ExceptionHandler(InterruptedException.class)
    public ResponseEntity<Result<Void>> handleInterrupted(InterruptedException exception, HttpServletRequest request) {
        if (request.getAttribute(RequestTimeoutFilter.TIMEOUT_ATTRIBUTE) == null) {
            return handleUnexpected(exception, request);
        }
        log.warn("异步请求处理超时并中断执行线程 [{} {}]", request.getMethod(), request.getRequestURI());
        return build(ResultCode.SERVICE_UNAVAILABLE.code(), ResultCode.SERVICE_UNAVAILABLE.httpStatus(),
                "请求处理超时");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Result<Void>> handleMultipart(MultipartException exception, HttpServletRequest request) {
        if (isSizeExceeded(exception)) {
            log.warn("上传内容超过限制 [{} {}] {}", request.getMethod(), request.getRequestURI(),
                    ExceptionUtils.getMessage(exception));
            return build(ResultCode.PAYLOAD_TOO_LARGE.code(), ResultCode.PAYLOAD_TOO_LARGE.httpStatus(),
                    ResultCode.PAYLOAD_TOO_LARGE.message());
        }
        log.warn("文件上传失败 [{} {}] {}", request.getMethod(), request.getRequestURI(),
                ExceptionUtils.getMessage(exception));
        return build(ResultCode.BAD_REQUEST.code(), ResultCode.BAD_REQUEST.httpStatus(), "文件上传失败");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
        if (properties.isLogStackTrace()) {
            log.error("未处理异常 [{} {}]", request.getMethod(), request.getRequestURI(), exception);
        } else {
            log.error("未处理异常 [{} {}] {}", request.getMethod(), request.getRequestURI(),
                    ExceptionUtils.buildMessage(exception));
        }
        String message = properties.isExposeMessage() && exception.getMessage() != null
                ? exception.getMessage()
                : properties.getDefaultMessage();
        return build(ResultCode.INTERNAL_SERVER_ERROR.code(),
                ResultCode.INTERNAL_SERVER_ERROR.httpStatus(), message);
    }

    private ResponseEntity<Result<Void>> validationFailure(List<String> messages, HttpServletRequest request) {
        String message = messages.isEmpty() ? ResultCode.BAD_REQUEST.message() : String.join("；", messages);
        log.warn("参数校验失败 [{} {}] {}", request.getMethod(), request.getRequestURI(), message);
        return build(ResultCode.BAD_REQUEST.code(), ResultCode.BAD_REQUEST.httpStatus(), message);
    }

    private boolean isSizeExceeded(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String name = current.getClass().getSimpleName();
            if (name.contains("SizeLimitExceeded") || name.contains("MaxUploadSizeExceeded")) {
                return true;
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return false;
    }

    private ResponseEntity<Result<Void>> build(int code, int httpStatus, String message) {
        HttpStatus status = HttpStatus.resolve(httpStatus);
        Result<Void> body = Result.fail(code, message);
        return ResponseEntity.status(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status).body(body);
    }
}
