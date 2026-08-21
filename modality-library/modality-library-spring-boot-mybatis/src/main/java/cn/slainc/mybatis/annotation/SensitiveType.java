package cn.slainc.mybatis.annotation;

public enum SensitiveType {

    DEFAULT,
    NAME("保留姓氏"),
    ID_CARD("身份证"),
    PHONE("手机号"),
    EMAIL("邮箱"),
    BANK_CARD("银行卡"),
    PASSWORD("密码"),
    ADDRESS("地址");

    private final String description;

    SensitiveType() {
        this("默认");
    }

    SensitiveType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCustom() {
        return this == DEFAULT;
    }
}
