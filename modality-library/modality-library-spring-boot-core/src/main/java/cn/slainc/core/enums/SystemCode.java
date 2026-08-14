package cn.slainc.core.enums;

public enum SystemCode implements DictEnum {

    SUPER_ADMIN(1L, "超级管理员"),
    SYSTEM_TENANT(1L, "系统租户"),
    SYSTEM_ROLE(1L, "系统角色"),
    SYSTEM_DEPARTMENT(1L, "系统部门");

    private final Long value;
    private final String label;

    SystemCode(Long value, String label) {
        this.value = value;
        this.label = label;
    }

    @Override
    public Long value() {
        return value;
    }

    @Override
    public String label() {
        return label;
    }

    public Long id() {
        return value;
    }
}
