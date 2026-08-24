package cn.slainc.redis.stream;

public enum StreamRetryResult {

    RETRIED("已重新投递"),
    DEAD_LETTERED("已转入死信"),
    MISSING("消息不存在");

    private final String description;

    StreamRetryResult(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
