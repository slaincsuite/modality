package cn.slainc.excel.chart;

import java.util.ArrayList;
import java.util.List;

public final class PivotSpec {

    public record DataColumn(PivotFunction function, int columnIndex, String name) {

        public DataColumn {
            if (function == null) {
                throw new IllegalArgumentException("透视表汇总方式不能为空");
            }
            name = name == null || name.isBlank() ? function.description() : name;
        }
    }

    private final String sourceRange;
    private final String position;
    private final int sheetIndex;
    private final List<Integer> rowLabels;
    private final List<Integer> columnLabels;
    private final List<DataColumn> dataColumns;

    private PivotSpec(Builder builder) {
        this.sourceRange = builder.sourceRange;
        this.position = builder.position;
        this.sheetIndex = builder.sheetIndex;
        this.rowLabels = List.copyOf(builder.rowLabels);
        this.columnLabels = List.copyOf(builder.columnLabels);
        this.dataColumns = List.copyOf(builder.dataColumns);
    }

    public static Builder builder(String sourceRange, String position) {
        return new Builder(sourceRange, position);
    }

    public String getSourceRange() {
        return sourceRange;
    }

    public String getPosition() {
        return position;
    }

    public int getSheetIndex() {
        return sheetIndex;
    }

    public List<Integer> getRowLabels() {
        return rowLabels;
    }

    public List<Integer> getColumnLabels() {
        return columnLabels;
    }

    public List<DataColumn> getDataColumns() {
        return dataColumns;
    }

    public static final class Builder {

        private final String sourceRange;
        private final String position;
        private int sheetIndex;
        private final List<Integer> rowLabels = new ArrayList<>();
        private final List<Integer> columnLabels = new ArrayList<>();
        private final List<DataColumn> dataColumns = new ArrayList<>();

        private Builder(String sourceRange, String position) {
            if (sourceRange == null || sourceRange.isBlank()) {
                throw new IllegalArgumentException("透视表数据源区域不能为空");
            }
            if (position == null || position.isBlank()) {
                throw new IllegalArgumentException("透视表位置不能为空");
            }
            this.sourceRange = sourceRange;
            this.position = position;
        }

        public Builder sheetIndex(int value) {
            this.sheetIndex = value;
            return this;
        }

        public Builder rowLabel(int columnIndex) {
            rowLabels.add(columnIndex);
            return this;
        }

        public Builder columnLabel(int columnIndex) {
            columnLabels.add(columnIndex);
            return this;
        }

        public Builder dataColumn(PivotFunction function, int columnIndex, String name) {
            dataColumns.add(new DataColumn(function, columnIndex, name));
            return this;
        }

        public PivotSpec build() {
            if (dataColumns.isEmpty()) {
                dataColumns.add(new DataColumn(PivotFunction.SUM, rowLabels.isEmpty() ? 0 : rowLabels.get(0) + 1, null));
            }
            return new PivotSpec(this);
        }
    }
}
