package cn.slainc.redis.ratelimit;

public enum RateLimitType {

    TOKEN_BUCKET("令牌桶"),
    SLIDING_WINDOW("滑动窗口"),
    LEAKY_BUCKET("漏桶"),
    FIXED_WINDOW("固定窗口");

    private final String description;

    RateLimitType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
