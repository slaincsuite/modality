package cn.slainc.core.enums;

public enum DeleteStatus implements DictEnum {

    NOT_DELETED(0, "未删除"),
    DELETED(1, "已删除");

    private final int value;
    private final String label;

    DeleteStatus(int value, String label) {
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

    public static DeleteStatus of(Integer value) {
        if (value == null) {
            return NOT_DELETED;
        }
        return value == DELETED.value ? DELETED : NOT_DELETED;
    }

    public boolean deleted() {
        return this == DELETED;
    }
}
