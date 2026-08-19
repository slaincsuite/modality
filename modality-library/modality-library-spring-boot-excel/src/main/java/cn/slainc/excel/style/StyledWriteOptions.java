package cn.slainc.excel.style;

public final class StyledWriteOptions {

    private String sheetName;
    private StyleSpec headerStyle;
    private StyleSpec bodyStyle;
    private StyleSpec stripeStyle;
    private Float rowHeight;

    public static StyledWriteOptions defaults() {
        return new StyledWriteOptions();
    }

    public static StyledWriteOptions of(String sheetName, StyleSpec bodyStyle) {
        StyledWriteOptions options = new StyledWriteOptions();
        options.sheetName = sheetName;
        options.bodyStyle = bodyStyle;
        return options;
    }

    public String getSheetName() {
        return sheetName;
    }

    public StyledWriteOptions sheetName(String value) {
        this.sheetName = value;
        return this;
    }

    public StyleSpec getHeaderStyle() {
        return headerStyle;
    }

    public StyledWriteOptions headerStyle(StyleSpec value) {
        this.headerStyle = value;
        return this;
    }

    public StyleSpec getBodyStyle() {
        return bodyStyle;
    }

    public StyledWriteOptions bodyStyle(StyleSpec value) {
        this.bodyStyle = value;
        return this;
    }

    public StyleSpec getStripeStyle() {
        return stripeStyle;
    }

    public StyledWriteOptions stripeStyle(StyleSpec value) {
        this.stripeStyle = value;
        return this;
    }

    public Float getRowHeight() {
        return rowHeight;
    }

    public StyledWriteOptions rowHeight(Float value) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException("行高必须大于 0");
        }
        this.rowHeight = value;
        return this;
    }
}
