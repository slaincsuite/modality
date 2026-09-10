package cn.slainc.jwt.integration;

public enum JwtStpMode {

    SIMPLE("简单模式：JWT 仅替换令牌样式，会话仍存服务端"),

    MIXIN("混合模式：部分逻辑走 JWT，部分能力保留服务端会话"),

    STATELESS("无状态模式：完全依赖 JWT 自包含，不查服务端会话");

    private final String description;

    JwtStpMode(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
