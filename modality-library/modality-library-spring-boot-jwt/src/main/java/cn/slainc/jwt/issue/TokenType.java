package cn.slainc.jwt.issue;

import java.time.Duration;

public enum TokenType {

    ACCESS("访问令牌", Duration.ofHours(2)),

    REFRESH("刷新令牌", Duration.ofDays(30)),

    ID_TOKEN("身份令牌", Duration.ofMinutes(10)),

    ONE_SHOT("一次性令牌", Duration.ofMinutes(5));

    private final String description;

    private final Duration defaultTtl;

    TokenType(String description, Duration defaultTtl) {
        this.description = description;
        this.defaultTtl = defaultTtl;
    }

    public String description() {
        return description;
    }

    public Duration defaultTtl() {
        return defaultTtl;
    }

    public static TokenType of(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
