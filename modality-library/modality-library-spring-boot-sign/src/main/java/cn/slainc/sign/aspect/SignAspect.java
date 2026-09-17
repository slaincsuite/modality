package cn.slainc.sign.aspect;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.sign.annotation.VerifySign;
import cn.slainc.sign.config.SignProperties;
import cn.slainc.sign.verify.SignVerifyResult;
import cn.slainc.sign.verify.SignVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class SignAspect {

    private final SignVerifier verifier;

    private final SignProperties properties;

    public SignAspect(SignVerifier verifier, SignProperties properties) {
        if (verifier == null) {
            throw new IllegalArgumentException("SignVerifier 不能为空");
        }
        this.verifier = verifier;
        this.properties = properties;
    }

    @Around("@annotation(cn.slainc.sign.annotation.VerifySign) || @within(cn.slainc.sign.annotation.VerifySign)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        if (!properties.isEnabled()) {
            return point.proceed();
        }
        Object target = point.getTarget();
        Class<?> targetClass = target == null ? null : AopUtils.getTargetClass(target);
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), targetClass);
        VerifySign annotation = AnnotatedElementUtils.findMergedAnnotation(method, VerifySign.class);
        if (annotation == null && targetClass != null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, VerifySign.class);
        }
        if (annotation == null) {
            return point.proceed();
        }
        HttpServletRequest request = currentRequest();
        if (request == null) {
            throw new BizException(ResultCode.BUSINESS_ERROR, "当前调用不在 HTTP 请求上下文中，无法校验签名");
        }
        SignProperties.Header header = properties.getHeader();
        String appId = firstNonBlank(annotation.appId(), request.getHeader(header.getAppId()));
        String algorithm = firstNonBlank(annotation.algorithm(), request.getHeader(header.getAlgorithm()));
        String timestamp = request.getHeader(header.getTimestamp());
        String nonce = request.getHeader(header.getNonce());
        String sign = request.getHeader(header.getSign());
        SignVerifyResult result = verifier.verify(appId, algorithm, annotation.version(), timestamp, nonce, sign,
                parameters(request), annotation.windowSeconds(), annotation.includeEmpty());
        if (!result.success()) {
            throw new BizException(ResultCode.BUSINESS_ERROR, result.reason());
        }
        return point.proceed();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private Map<String, List<String>> parameters(HttpServletRequest request) {
        Map<String, String[]> raw = request.getParameterMap();
        Map<String, List<String>> params = new LinkedHashMap<>();
        raw.forEach((name, values) -> params.put(name, values == null ? List.of() : Arrays.asList(values)));
        return params;
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }
}
