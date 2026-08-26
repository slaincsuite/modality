package cn.slainc.tenant.resolver;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DomainTenantResolver implements TenantResolver {

    public static final int ORDER = 200;

    private static final String HOST_HEADER = "Host";

    private static final Logger log = LoggerFactory.getLogger(DomainTenantResolver.class);

    private final String rootDomain;

    private final Set<String> ignoredSubdomains;

    private final TenantLookup lookup;

    public DomainTenantResolver(String rootDomain, Collection<String> ignoredSubdomains, TenantLookup lookup) {
        this.rootDomain = normalizeDomain(rootDomain);
        this.ignoredSubdomains = new LinkedHashSet<>();
        if (ignoredSubdomains != null) {
            for (String item : ignoredSubdomains) {
                String value = normalizeDomain(item);
                if (!value.isEmpty()) {
                    this.ignoredSubdomains.add(value);
                }
            }
        }
        this.lookup = lookup;
    }

    @Override
    public Long resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String host = host(request);
        for (String candidate : candidates(host)) {
            Long tenantId = TenantIds.parse(candidate, lookup);
            if (tenantId != null) {
                log.debug("域名 [{}] 解析出租户 [{}]", host, tenantId);
                return tenantId;
            }
        }
        return null;
    }

    @Override
    public int order() {
        return ORDER;
    }

    public String rootDomain() {
        return rootDomain;
    }

    private List<String> candidates(String host) {
        if (host.isEmpty() || "localhost".equals(host) || isNumericHost(host)) {
            return List.of();
        }
        if (!rootDomain.isEmpty()) {
            String suffix = "." + rootDomain;
            if (!host.endsWith(suffix) || host.length() == suffix.length()) {
                return List.of();
            }
            String prefix = host.substring(0, host.length() - suffix.length());
            return usable(prefix, firstLabel(prefix));
        }
        String[] labels = host.split("\\.");
        if (labels.length < 3) {
            return List.of();
        }
        return usable(labels[0]);
    }

    private List<String> usable(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String normalized = normalizeDomain(value);
            if (normalized.isEmpty() || ignoredSubdomains.contains(normalized) || result.contains(normalized)) {
                continue;
            }
            result.add(normalized);
        }
        return result;
    }

    private static String host(HttpServletRequest request) {
        String serverName = request.getServerName();
        if (serverName == null || serverName.isBlank()) {
            serverName = request.getHeader(HOST_HEADER);
        }
        if (serverName == null) {
            return "";
        }
        String host = serverName.trim().toLowerCase(Locale.ROOT);
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }

    private static String firstLabel(String host) {
        int index = host.indexOf('.');
        return index <= 0 ? host : host.substring(0, index);
    }

    private static boolean isNumericHost(String host) {
        for (int index = 0; index < host.length(); index++) {
            char value = host.charAt(index);
            if (!Character.isDigit(value) && value != '.') {
                return false;
            }
        }
        return true;
    }

    private static String normalizeDomain(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
