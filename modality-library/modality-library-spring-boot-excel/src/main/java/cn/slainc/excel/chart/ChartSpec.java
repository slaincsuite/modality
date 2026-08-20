package cn.slainc.excel.chart;

import java.util.ArrayList;
import java.util.List;

public final class ChartSpec {

    private final ChartType type;
    private final String title;
    private final int sheetIndex;
    private final int column1;
    private final int row1;
    private final int column2;
    private final int row2;
    private final List<ChartSeries> series;

    private ChartSpec(Builder builder) {
        this.type = builder.type;
        this.title = builder.title;
        this.sheetIndex = builder.sheetIndex;
        this.column1 = builder.column1;
        this.row1 = builder.row1;
        this.column2 = builder.column2;
        this.row2 = builder.row2;
        this.series = List.copyOf(builder.series);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ChartSpec bar(String title, ChartSeries series) {
        return builder().type(ChartType.BAR).title(title).series(series).build();
    }

    public static ChartSpec line(String title, ChartSeries series) {
        return builder().type(ChartType.LINE).title(title).series(series).build();
    }

    public static ChartSpec pie(String title, ChartSeries series) {
        return builder().type(ChartType.PIE).title(title).series(series).build();
    }

    public ChartType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public int getSheetIndex() {
        return sheetIndex;
    }

    public int getColumn1() {
        return column1;
    }

    public int getRow1() {
        return row1;
    }

    public int getColumn2() {
        return column2;
    }

    public int getRow2() {
        return row2;
    }

    public List<ChartSeries> getSeries() {
        return series;
    }

    public static final class Builder {

        private ChartType type = ChartType.BAR;
        private String title = "图表";
        private int sheetIndex;
        private int column1;
        private int row1;
        private int column2;
        private int row2;
        private final List<ChartSeries> series = new ArrayList<>();

        public Builder type(ChartType value) {
            this.type = value;
            return this;
        }

        public Builder title(String value) {
            this.title = value;
            return this;
        }

        public Builder sheetIndex(int value) {
            this.sheetIndex = value;
            return this;
        }

        public Builder anchor(int fromColumn, int fromRow, int toColumn, int toRow) {
            this.column1 = fromColumn;
            this.row1 = fromRow;
            this.column2 = toColumn;
            this.row2 = toRow;
            return this;
        }

        public Builder series(ChartSeries value) {
            if (value != null) {
                this.series.add(value);
            }
            return this;
        }

        public ChartSpec build() {
            if (series.isEmpty()) {
                throw new IllegalArgumentException("图表至少需要一个数据系列");
            }
            if (column2 <= column1 || row2 <= row1) {
                throw new IllegalArgumentException("图表锚点区域非法");
            }
            return new ChartSpec(this);
        }
    }
}
