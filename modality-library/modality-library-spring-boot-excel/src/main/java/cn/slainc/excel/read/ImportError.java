package cn.slainc.excel.read;

public record ImportError(int rowIndex, String field, Object value, String message) {

    public static ImportError of(int rowIndex, String field, Object value, String message) {
        return new ImportError(rowIndex, field, value, message);
    }

    public static ImportError unknownRow(String field, Object value, String message) {
        return new ImportError(-1, field, value, message);
    }

    public String describe() {
        if (rowIndex < 0) {
            return field == null || field.isBlank() ? message : "字段 " + field + ": " + message;
        }
        StringBuilder builder = new StringBuilder("第 ").append(rowIndex).append(" 行");
        if (field != null && !field.isBlank()) {
            builder.append(" 字段 ").append(field);
        }
        builder.append(": ").append(message);
        return builder.toString();
    }
}
