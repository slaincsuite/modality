package cn.slainc.modules.upms.entity;

import cn.slainc.core.exception.BizException;

public enum DataScopeType {

    ALL("全部数据"),

    DEPT("本部门"),

    DEPT_AND_CHILD("本部门及以下"),

    SELF("仅本人"),

    CUSTOM("自定义部门");

    private final String label;

    DataScopeType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static DataScopeType of(String code) {
        for (DataScopeType type : values()) {
            if (type.name().equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new BizException("非法的数据权限范围: " + code
                + "，只允许 ALL、DEPT、DEPT_AND_CHILD、SELF、CUSTOM");
    }
}
