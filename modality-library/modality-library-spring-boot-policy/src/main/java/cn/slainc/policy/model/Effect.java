package cn.slainc.policy.model;

public enum Effect {

    ALLOW("allow"),

    DENY("deny");

    private final String code;

    Effect(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Effect of(String code) {
        for (Effect effect : values()) {
            if (effect.code.equalsIgnoreCase(code) || effect.name().equalsIgnoreCase(code)) {
                return effect;
            }
        }
        throw new IllegalArgumentException("非法的策略效果: " + code + "，只允许 allow 或 deny");
    }
}
