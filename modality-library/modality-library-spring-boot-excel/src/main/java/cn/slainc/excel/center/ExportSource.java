package cn.slainc.excel.center;

public record ExportSource<T>(Class<T> head, String name, String sheetName, ExportDataProvider<T> provider) {

    public ExportSource {
        if (head == null) {
            throw new IllegalArgumentException("导出行类型不能为空");
        }
        if (provider == null) {
            throw new IllegalArgumentException("导出数据源不能为空");
        }
        name = name == null || name.isBlank() ? "导出任务" : name;
        sheetName = sheetName == null || sheetName.isBlank() ? "导出数据" : sheetName;
    }

    public static <T> ExportSource<T> of(Class<T> head, String name, ExportDataProvider<T> provider) {
        return new ExportSource<>(head, name, null, provider);
    }

    public ExportSource<T> withSheetName(String value) {
        return new ExportSource<>(head, name, value, provider);
    }
}
