package cn.slainc.redis.ratelimit;

public enum RateLimitDimension {

    IP("客户端 IP"),
    USER("登录用户"),
    API("接口"),
    CUSTOM("自定义 SpEL"),
    GLOBAL("全局");

    private final String description;

    RateLimitDimension(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
