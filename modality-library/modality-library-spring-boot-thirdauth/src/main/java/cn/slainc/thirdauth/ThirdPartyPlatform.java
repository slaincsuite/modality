package cn.slainc.thirdauth;

public enum ThirdPartyPlatform {

    WECHAT("wechat", "微信"),

    WECHAT_WORK("wechat_work", "企业微信"),

    FEISHU("feishu", "飞书"),

    DINGTALK("dingtalk", "钉钉");

    private final String code;

    private final String label;

    ThirdPartyPlatform(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static ThirdPartyPlatform of(String code) {
        if (code != null) {
            String normalized = code.trim().toLowerCase().replace('-', '_');
            for (ThirdPartyPlatform platform : values()) {
                if (platform.code.equals(normalized) || platform.name().equalsIgnoreCase(normalized)) {
                    return platform;
                }
            }
        }
        throw new IllegalArgumentException("不支持的第三方平台: " + code + "，支持 " + supported());
    }

    public static String supported() {
        StringBuilder builder = new StringBuilder();
        for (ThirdPartyPlatform platform : values()) {
            if (!builder.isEmpty()) {
                builder.append("、");
            }
            builder.append(platform.code);
        }
        return builder.toString();
    }
}
