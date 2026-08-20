package cn.slainc.excel.chart;

public record ChartSeries(String title, String categoriesRange, String valuesRange) {

    public ChartSeries {
        if (valuesRange == null || valuesRange.isBlank()) {
            throw new IllegalArgumentException("图表数据区域不能为空");
        }
        if (categoriesRange == null || categoriesRange.isBlank()) {
            throw new IllegalArgumentException("图表分类区域不能为空");
        }
        title = title == null || title.isBlank() ? "系列" : title;
    }

    public static ChartSeries of(String title, String categoriesRange, String valuesRange) {
        return new ChartSeries(title, categoriesRange, valuesRange);
    }
}
