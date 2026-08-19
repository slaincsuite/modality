package cn.slainc.excel.write;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SheetOptions {

    private int freezeRows;
    private int freezeColumns;
    private boolean autoFilter;
    private int autoFilterColumnCount;
    private final Map<Integer, Integer> columnWidths = new LinkedHashMap<>();
    private final Map<Integer, List<String>> dropdowns = new LinkedHashMap<>();

    public static SheetOptions defaults() {
        return new SheetOptions();
    }

    public static SheetOptions frozen(int rows, int columns) {
        SheetOptions options = new SheetOptions();
        options.freezeRows = rows;
        options.freezeColumns = columns;
        return options;
    }

    public int getFreezeRows() {
        return freezeRows;
    }

    public SheetOptions freeze(int rows, int columns) {
        this.freezeRows = rows;
        this.freezeColumns = columns;
        return this;
    }

    public int getFreezeColumns() {
        return freezeColumns;
    }

    public boolean isAutoFilter() {
        return autoFilter;
    }

    public SheetOptions autoFilter(int columnCount) {
        this.autoFilter = true;
        this.autoFilterColumnCount = columnCount;
        return this;
    }

    public int getAutoFilterColumnCount() {
        return autoFilterColumnCount;
    }

    public Map<Integer, Integer> getColumnWidths() {
        return columnWidths;
    }

    public SheetOptions columnWidth(int columnIndex, int widthInCharacters) {
        columnWidths.put(columnIndex, widthInCharacters);
        return this;
    }

    public Map<Integer, List<String>> getDropdowns() {
        return dropdowns;
    }

    public SheetOptions dropdown(int columnIndex, List<String> values) {
        if (values != null && !values.isEmpty()) {
            dropdowns.put(columnIndex, new ArrayList<>(values));
        }
        return this;
    }

    public boolean isEmpty() {
        return freezeRows <= 0 && freezeColumns <= 0 && !autoFilter && columnWidths.isEmpty() && dropdowns.isEmpty();
    }
}
