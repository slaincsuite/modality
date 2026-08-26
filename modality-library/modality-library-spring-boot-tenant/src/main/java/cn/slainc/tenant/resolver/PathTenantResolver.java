package cn.slainc.tenant.resolver;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;

public class PathTenantResolver implements TenantResolver {

    public static final int ORDER = 300;

    private final String prefix;

    private final int index;

    private final TenantLookup lookup;

    public PathTenantResolver(String prefix, int index, TenantLookup lookup) {
        this.prefix = prefix == null || prefix.isBlank() ? "/api" : prefix.trim();
        this.index = Math.max(index, 0);
        this.lookup = lookup;
    }

    @Override
    public Long resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        List<String> prefixSegments = segments(prefix);
        List<String> pathSegments = segments(path(request));
        if (pathSegments.size() <= prefixSegments.size()) {
            return null;
        }
        if (!pathSegments.subList(0, prefixSegments.size()).equals(prefixSegments)) {
            return null;
        }
        int position = prefixSegments.size() + index;
        if (position >= pathSegments.size()) {
            return null;
        }
        return TenantIds.parse(pathSegments.get(position), lookup);
    }

    @Override
    public int order() {
        return ORDER;
    }

    public String prefix() {
        return prefix;
    }

    public int index() {
        return index;
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isEmpty()) {
            uri = request.getServletPath();
        }
        if (uri == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return uri;
    }

    private static List<String> segments(String value) {
        List<String> result = new ArrayList<>();
        for (String part : value.split("/")) {
            if (!part.isBlank()) {
                result.add(part);
            }
        }
        return result;
    }
}
