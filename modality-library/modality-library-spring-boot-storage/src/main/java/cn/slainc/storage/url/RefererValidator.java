package cn.slainc.storage.url;

import cn.slainc.storage.StorageException;

import java.net.URI;
import java.util.List;

public class RefererValidator {

    private final List<String> allowedHosts;

    private final boolean allowEmpty;

    public RefererValidator(List<String> allowedHosts, boolean allowEmpty) {
        this.allowedHosts = allowedHosts == null ? List.of() : List.copyOf(allowedHosts);
        this.allowEmpty = allowEmpty;
    }

    public boolean enabled() {
        return !allowedHosts.isEmpty();
    }

    public boolean allowed(String referer) {
        if (!enabled()) {
            return true;
        }
        if (referer == null || referer.isBlank()) {
            return allowEmpty;
        }
        String host = host(referer);
        if (host == null) {
            return false;
        }
        for (String allowed : allowedHosts) {
            if (allowed.startsWith("*.")) {
                String suffix = allowed.substring(1);
                if (host.endsWith(suffix) && host.length() > suffix.length()) {
                    return true;
                }
            } else if (allowed.equalsIgnoreCase(host)) {
                return true;
            }
        }
        return false;
    }

    public void validate(String referer) {
        if (!allowed(referer)) {
            throw new StorageException("防盗链校验未通过，来源不被允许：" + (referer == null ? "空" : referer));
        }
    }

    private static String host(String referer) {
        try {
            String value = referer.contains("://") ? referer : "http://" + referer;
            URI uri = URI.create(value);
            return uri.getHost();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
