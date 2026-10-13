package cn.slainc.workorder.template;


public enum WorkOrderFieldType {

    TEXT("单行文本"),
    TEXTAREA("多行文本"),
    NUMBER("数字"),
    DATE("日期"),
    DATETIME("日期时间"),
    SELECT("单选"),
    MULTISELECT("多选"),
    FILE("文件"),
    RICHTEXT("富文本"),
    USER("用户"),
    DEPT("部门");

    private final String description;

    WorkOrderFieldType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTextual() {
        return this == TEXT || this == TEXTAREA || this == RICHTEXT || this == FILE
                || this == USER || this == DEPT || this == DATE || this == DATETIME;
    }
}
