package cn.slainc.jwt.introspect;

import java.util.Map;

public record IntrospectionResult(boolean active, Map<String, Object> claims, String tokenType, Long exp) {

    public static IntrospectionResult inactive() {
        return new IntrospectionResult(false, Map.of(), null, null);
    }
}
