package cn.slainc.excel.style;

public enum NumberPattern {

    GENERAL("", "常规"),
    INTEGER("#,##0", "整数"),
    DECIMAL("#,##0.00", "两位小数"),
    MONEY("¥#,##0.00", "金额"),
    PERCENT("0.00%", "百分比"),
    DATE("yyyy-mm-dd", "日期"),
    DATETIME("yyyy-mm-dd hh:mm:ss", "日期时间"),
    TEXT("@", "文本");

    private final String pattern;
    private final String description;

    NumberPattern(String pattern, String description) {
        this.pattern = pattern;
        this.description = description;
    }

    public String pattern() {
        return pattern;
    }

    public String description() {
        return description;
    }

    public boolean isGeneral() {
        return this == GENERAL;
    }
}
