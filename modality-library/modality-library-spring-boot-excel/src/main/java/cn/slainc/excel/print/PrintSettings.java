package cn.slainc.excel.print;

import java.util.ArrayList;
import java.util.List;

public final class PrintSettings {

    private PaperSize paperSize = PaperSize.A4;
    private PageOrientation orientation = PageOrientation.PORTRAIT;
    private Short scale;
    private Short fitToWidth;
    private Short fitToHeight;
    private String printArea;
    private Integer repeatTitleRowFrom;
    private Integer repeatTitleRowTo;
    private Integer repeatTitleColumnFrom;
    private Integer repeatTitleColumnTo;
    private String headerLeft;
    private String headerCenter;
    private String headerRight;
    private String footerLeft;
    private String footerCenter;
    private String footerRight;
    private final List<Integer> rowBreaks = new ArrayList<>();
    private final List<Integer> columnBreaks = new ArrayList<>();
    private Double marginLeft;
    private Double marginRight;
    private Double marginTop;
    private Double marginBottom;
    private Boolean printGridlines;
    private Boolean horizontallyCenter;
    private Boolean verticallyCenter;

    public static PrintSettings defaults() {
        return new PrintSettings();
    }

    public static PrintSettings landscape() {
        PrintSettings settings = new PrintSettings();
        settings.orientation = PageOrientation.LANDSCAPE;
        return settings;
    }

    public PaperSize getPaperSize() {
        return paperSize;
    }

    public PrintSettings paperSize(PaperSize value) {
        this.paperSize = value == null ? PaperSize.A4 : value;
        return this;
    }

    public PageOrientation getOrientation() {
        return orientation;
    }

    public PrintSettings orientation(PageOrientation value) {
        this.orientation = value == null ? PageOrientation.PORTRAIT : value;
        return this;
    }

    public Short getScale() {
        return scale;
    }

    public PrintSettings scale(int value) {
        if (value < 10 || value > 400) {
            throw new IllegalArgumentException("缩放比例必须在 10 到 400 之间");
        }
        this.scale = (short) value;
        return this;
    }

    public Short getFitToWidth() {
        return fitToWidth;
    }

    public Short getFitToHeight() {
        return fitToHeight;
    }

    public PrintSettings fitToPage(int widthPages, int heightPages) {
        if (widthPages < 1 || heightPages < 1) {
            throw new IllegalArgumentException("适应页数必须大于 0");
        }
        this.fitToWidth = (short) widthPages;
        this.fitToHeight = (short) heightPages;
        return this;
    }

    public String getPrintArea() {
        return printArea;
    }

    public PrintSettings printArea(String value) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException("打印区域不能为空字符串");
        }
        this.printArea = value;
        return this;
    }

    public Integer getRepeatTitleRowFrom() {
        return repeatTitleRowFrom;
    }

    public Integer getRepeatTitleRowTo() {
        return repeatTitleRowTo;
    }

    public PrintSettings repeatTitleRows(int fromRow, int toRow) {
        if (fromRow < 1 || toRow < fromRow) {
            throw new IllegalArgumentException("重复标题行区间非法: " + fromRow + "-" + toRow);
        }
        this.repeatTitleRowFrom = fromRow - 1;
        this.repeatTitleRowTo = toRow - 1;
        return this;
    }

    public Integer getRepeatTitleColumnFrom() {
        return repeatTitleColumnFrom;
    }

    public Integer getRepeatTitleColumnTo() {
        return repeatTitleColumnTo;
    }

    public PrintSettings repeatTitleColumns(int fromColumn, int toColumn) {
        if (fromColumn < 1 || toColumn < fromColumn) {
            throw new IllegalArgumentException("重复标题列区间非法: " + fromColumn + "-" + toColumn);
        }
        this.repeatTitleColumnFrom = fromColumn - 1;
        this.repeatTitleColumnTo = toColumn - 1;
        return this;
    }

    public String getHeaderLeft() {
        return headerLeft;
    }

    public PrintSettings headerLeft(String value) {
        this.headerLeft = value;
        return this;
    }

    public String getHeaderCenter() {
        return headerCenter;
    }

    public PrintSettings headerCenter(String value) {
        this.headerCenter = value;
        return this;
    }

    public String getHeaderRight() {
        return headerRight;
    }

    public PrintSettings headerRight(String value) {
        this.headerRight = value;
        return this;
    }

    public String getFooterLeft() {
        return footerLeft;
    }

    public PrintSettings footerLeft(String value) {
        this.footerLeft = value;
        return this;
    }

    public String getFooterCenter() {
        return footerCenter;
    }

    public PrintSettings footerCenter(String value) {
        this.footerCenter = value;
        return this;
    }

    public String getFooterRight() {
        return footerRight;
    }

    public PrintSettings footerRight(String value) {
        this.footerRight = value;
        return this;
    }

    public List<Integer> getRowBreaks() {
        return rowBreaks;
    }

    public PrintSettings rowBreak(int rowIndex) {
        rowBreaks.add(rowIndex);
        return this;
    }

    public List<Integer> getColumnBreaks() {
        return columnBreaks;
    }

    public PrintSettings columnBreak(int columnIndex) {
        columnBreaks.add(columnIndex);
        return this;
    }

    public Double getMarginLeft() {
        return marginLeft;
    }

    public PrintSettings marginLeft(double value) {
        this.marginLeft = value;
        return this;
    }

    public Double getMarginRight() {
        return marginRight;
    }

    public PrintSettings marginRight(double value) {
        this.marginRight = value;
        return this;
    }

    public Double getMarginTop() {
        return marginTop;
    }

    public PrintSettings marginTop(double value) {
        this.marginTop = value;
        return this;
    }

    public Double getMarginBottom() {
        return marginBottom;
    }

    public PrintSettings marginBottom(double value) {
        this.marginBottom = value;
        return this;
    }

    public Boolean getPrintGridlines() {
        return printGridlines;
    }

    public PrintSettings printGridlines(boolean value) {
        this.printGridlines = value;
        return this;
    }

    public Boolean getHorizontallyCenter() {
        return horizontallyCenter;
    }

    public PrintSettings horizontallyCenter(boolean value) {
        this.horizontallyCenter = value;
        return this;
    }

    public Boolean getVerticallyCenter() {
        return verticallyCenter;
    }

    public PrintSettings verticallyCenter(boolean value) {
        this.verticallyCenter = value;
        return this;
    }
}
