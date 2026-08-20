package cn.slainc.excel.chart;

public enum PivotFunction {

    SUM("求和"),
    COUNT("计数"),
    AVERAGE("平均值"),
    MAX("最大值"),
    MIN("最小值");

    private final String description;

    PivotFunction(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
