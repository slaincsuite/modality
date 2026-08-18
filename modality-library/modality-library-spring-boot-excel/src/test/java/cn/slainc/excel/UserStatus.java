package cn.slainc.excel;

import cn.slainc.core.enums.DictEnum;

public enum UserStatus implements DictEnum {

    ACTIVE(1, "正常"),
    LOCKED(2, "锁定");

    private final int value;
    private final String label;

    UserStatus(int value, String label) {
        this.value = value;
        this.label = label;
    }

    @Override
    public Integer value() {
        return value;
    }

    @Override
    public String label() {
        return label;
    }
}
