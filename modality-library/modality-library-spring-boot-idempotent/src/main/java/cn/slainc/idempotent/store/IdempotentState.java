package cn.slainc.idempotent.store;

public enum IdempotentState {

    PENDING("执行中"),
    SUCCESS("已完成"),
    TOKEN("待消费凭证");

    private final String description;

    IdempotentState(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
