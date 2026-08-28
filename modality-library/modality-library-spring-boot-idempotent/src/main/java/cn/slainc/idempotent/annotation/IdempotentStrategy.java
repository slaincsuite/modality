package cn.slainc.idempotent.annotation;

public enum IdempotentStrategy {

    TOKEN("Token 一次性凭证"),
    PARAMETER("参数指纹"),
    HYBRID("Token 与参数指纹混合"),
    CUSTOM("自定义扩展");

    private final String description;

    IdempotentStrategy(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
