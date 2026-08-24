package cn.slainc.redis.structure;

public enum RedisScriptResultType {

    INTEGER("整数"),
    VALUE("字符串"),
    MULTI("多值"),
    STATUS("状态"),
    BOOLEAN("布尔");

    private final String description;

    RedisScriptResultType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
