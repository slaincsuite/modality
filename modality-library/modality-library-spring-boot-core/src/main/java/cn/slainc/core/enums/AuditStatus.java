package cn.slainc.core.enums;

public enum AuditStatus implements DictEnum {

    PENDING(0, "待审核"),
    APPROVED(1, "已通过"),
    REJECTED(2, "已驳回");

    private final int value;
    private final String label;

    AuditStatus(int value, String label) {
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

    public static AuditStatus of(Integer value) {
        if (value == null) {
            return null;
        }
        for (AuditStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        return null;
    }
}
