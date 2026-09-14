package cn.slainc.oauth2.guard;

import cn.dev33.satoken.oauth2.exception.SaOAuth2Exception;
import cn.slainc.oauth2.client.ClientRecordCache;
import cn.slainc.oauth2.client.Oauth2ClientRecord;
import cn.slainc.oauth2.config.Oauth2Keys;
import cn.slainc.oauth2.config.Oauth2Properties;
import cn.slainc.redis.ratelimit.RateLimitType;
import cn.slainc.redis.ratelimit.RateLimiter;
import cn.slainc.utils.net.IpUtils;
import inet.ipaddr.IPAddressString;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Optional;

public class ClientGuard {

    private final ClientRecordCache cache;

    private final ObjectProvider<RateLimiter> rateLimiter;

    private final Oauth2Properties properties;

    public ClientGuard(ClientRecordCache cache, ObjectProvider<RateLimiter> rateLimiter,
                       Oauth2Properties properties) {
        this.cache = cache;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    public void check(Object loginId, String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return;
        }
        Optional<Oauth2ClientRecord> found = cache.get(clientId);
        if (found.isEmpty()) {
            return;
        }
        Oauth2ClientRecord record = found.get();
        checkIpWhitelist(clientId, record);
        checkRateLimit(clientId, record);
    }

    private void checkIpWhitelist(String clientId, Oauth2ClientRecord record) {
        if (record.getIpWhitelist() == null || record.getIpWhitelist().isEmpty()) {
            return;
        }
        HttpServletRequest request = currentRequest();
        String requestIp = request == null ? null
                : IpUtils.resolveClientIp(request.getHeader("X-Forwarded-For"), request.getHeader("X-Real-IP"),
                        request.getHeader("CF-Connecting-IP"), request.getRemoteAddr());
        if (requestIp == null) {
            throw new SaOAuth2Exception("客户端 " + clientId + " 的请求无法解析来源 IP，拒绝访问");
        }
        for (String entry : record.getIpWhitelist()) {
            if (matchesIp(requestIp, entry)) {
                return;
            }
        }
        throw new SaOAuth2Exception("客户端 " + clientId + " 的来源 IP 不在白名单：" + requestIp);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private void checkRateLimit(String clientId, Oauth2ClientRecord record) {
        long qps = record.getQpsLimit() > 0
                ? record.getQpsLimit() : properties.getGuard().getDefaultQpsLimit();
        if (qps <= 0) {
            return;
        }
        RateLimiter limiter = rateLimiter.getIfAvailable();
        if (limiter == null) {
            return;
        }
        boolean acquired = limiter.tryAcquire(Oauth2Keys.clientRateKey(clientId),
                RateLimitType.TOKEN_BUCKET, qps, Duration.ofSeconds(1));
        if (!acquired) {
            throw new SaOAuth2Exception("客户端 " + clientId + " 请求频率超过限制，请稍后重试");
        }
    }

    private boolean matchesIp(String requestIp, String entry) {
        if (requestIp == null || entry == null || entry.isBlank()) {
            return false;
        }
        if (entry.equals(requestIp)) {
            return true;
        }
        try {
            IPAddressString network = new IPAddressString(entry.trim());
            IPAddressString address = new IPAddressString(requestIp);
            return network.contains(address);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
