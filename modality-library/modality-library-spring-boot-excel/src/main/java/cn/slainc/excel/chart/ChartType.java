package cn.slainc.excel.chart;

public enum ChartType {

    BAR("柱状图"),
    LINE("折线图"),
    PIE("饼图");

    private final String description;

    ChartType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
