package cn.slainc.openapi.domain;

public enum AppAction {

    SUBMIT("提交审核"),

    APPROVE("审核通过"),

    REJECT("审核驳回"),

    OFFLINE("下线"),

    REPUBLISH("重新上架");

    private final String label;

    AppAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
