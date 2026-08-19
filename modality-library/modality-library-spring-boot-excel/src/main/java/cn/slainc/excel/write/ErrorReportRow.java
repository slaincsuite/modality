package cn.slainc.excel.write;

import cn.idev.excel.annotation.ExcelProperty;

public class ErrorReportRow {

    @ExcelProperty("行号")
    private Integer rowNumber;

    @ExcelProperty("字段")
    private String field;

    @ExcelProperty("原始值")
    private String value;

    @ExcelProperty("错误信息")
    private String message;

    public ErrorReportRow() {
    }

    public ErrorReportRow(Integer rowNumber, String field, String value, String message) {
        this.rowNumber = rowNumber;
        this.field = field;
        this.value = value;
        this.message = message;
    }

    public Integer getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(Integer rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
