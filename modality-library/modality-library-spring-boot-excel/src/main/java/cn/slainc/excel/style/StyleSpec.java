package cn.slainc.excel.style;

import java.util.Objects;

public final class StyleSpec {

    private final String fontName;
    private final Integer fontSize;
    private final Boolean bold;
    private final Boolean italic;
    private final Boolean underline;
    private final Boolean strikeout;
    private final String fontColor;
    private final String foregroundColor;
    private final String backgroundColor;
    private final String borderColor;
    private final BorderWeight border;
    private final CellAlign horizontal;
    private final VerticalAlign vertical;
    private final Boolean wrapText;
    private final Integer indent;
    private final Integer rotation;
    private final NumberPattern numberPattern;
    private final String dataFormat;

    private StyleSpec(Builder builder) {
        this.fontName = builder.fontName;
        this.fontSize = builder.fontSize;
        this.bold = builder.bold;
        this.italic = builder.italic;
        this.underline = builder.underline;
        this.strikeout = builder.strikeout;
        this.fontColor = builder.fontColor;
        this.foregroundColor = builder.foregroundColor;
        this.backgroundColor = builder.backgroundColor;
        this.borderColor = builder.borderColor;
        this.border = builder.border;
        this.horizontal = builder.horizontal;
        this.vertical = builder.vertical;
        this.wrapText = builder.wrapText;
        this.indent = builder.indent;
        this.rotation = builder.rotation;
        this.numberPattern = builder.numberPattern;
        this.dataFormat = builder.dataFormat;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static StyleSpec empty() {
        return builder().build();
    }

    public String getFontName() {
        return fontName;
    }

    public Integer getFontSize() {
        return fontSize;
    }

    public Boolean getBold() {
        return bold;
    }

    public Boolean getItalic() {
        return italic;
    }

    public Boolean getUnderline() {
        return underline;
    }

    public Boolean getStrikeout() {
        return strikeout;
    }

    public String getFontColor() {
        return fontColor;
    }

    public String getForegroundColor() {
        return foregroundColor;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public String getBorderColor() {
        return borderColor;
    }

    public BorderWeight getBorder() {
        return border;
    }

    public CellAlign getHorizontal() {
        return horizontal;
    }

    public VerticalAlign getVertical() {
        return vertical;
    }

    public Boolean getWrapText() {
        return wrapText;
    }

    public Integer getIndent() {
        return indent;
    }

    public Integer getRotation() {
        return rotation;
    }

    public NumberPattern getNumberPattern() {
        return numberPattern;
    }

    public String getDataFormat() {
        return dataFormat;
    }

    public boolean hasFont() {
        return fontName != null || fontSize != null || bold != null || italic != null || underline != null
                || strikeout != null || fontColor != null;
    }

    public boolean hasFill() {
        return foregroundColor != null || backgroundColor != null;
    }

    public String pattern() {
        if (dataFormat != null && !dataFormat.isBlank()) {
            return dataFormat;
        }
        return numberPattern == null || numberPattern.isGeneral() ? null : numberPattern.pattern();
    }

    public boolean isEmpty() {
        return !hasFont() && !hasFill() && borderColor == null && border == null && horizontal == null
                && vertical == null && wrapText == null && indent == null && rotation == null && pattern() == null;
    }

    public StyleSpec over(StyleSpec base) {
        if (base == null) {
            return this;
        }
        return builder()
                .fontName(pick(fontName, base.fontName))
                .fontSize(pick(fontSize, base.fontSize))
                .bold(pick(bold, base.bold))
                .italic(pick(italic, base.italic))
                .underline(pick(underline, base.underline))
                .strikeout(pick(strikeout, base.strikeout))
                .fontColor(pick(fontColor, base.fontColor))
                .foregroundColor(pick(foregroundColor, base.foregroundColor))
                .backgroundColor(pick(backgroundColor, base.backgroundColor))
                .borderColor(pick(borderColor, base.borderColor))
                .border(pick(border, base.border))
                .horizontal(pick(horizontal, base.horizontal))
                .vertical(pick(vertical, base.vertical))
                .wrapText(pick(wrapText, base.wrapText))
                .indent(pick(indent, base.indent))
                .rotation(pick(rotation, base.rotation))
                .numberPattern(pick(numberPattern, base.numberPattern))
                .dataFormat(pick(dataFormat, base.dataFormat))
                .build();
    }

    private static <T> T pick(T value, T fallback) {
        return value != null ? value : fallback;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StyleSpec spec)) {
            return false;
        }
        return Objects.equals(fontName, spec.fontName) && Objects.equals(fontSize, spec.fontSize)
                && Objects.equals(bold, spec.bold) && Objects.equals(italic, spec.italic)
                && Objects.equals(underline, spec.underline) && Objects.equals(strikeout, spec.strikeout)
                && Objects.equals(fontColor, spec.fontColor) && Objects.equals(foregroundColor, spec.foregroundColor)
                && Objects.equals(backgroundColor, spec.backgroundColor) && Objects.equals(borderColor, spec.borderColor)
                && border == spec.border && horizontal == spec.horizontal && vertical == spec.vertical
                && Objects.equals(wrapText, spec.wrapText) && Objects.equals(indent, spec.indent)
                && Objects.equals(rotation, spec.rotation) && numberPattern == spec.numberPattern
                && Objects.equals(dataFormat, spec.dataFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fontName, fontSize, bold, italic, underline, strikeout, fontColor, foregroundColor,
                backgroundColor, borderColor, border, horizontal, vertical, wrapText, indent, rotation, numberPattern,
                dataFormat);
    }

    @Override
    public String toString() {
        return "StyleSpec{fontName=" + fontName + ", fontSize=" + fontSize + ", bold=" + bold + ", italic=" + italic
                + ", foregroundColor=" + foregroundColor + ", border=" + border + ", horizontal=" + horizontal
                + ", vertical=" + vertical + ", wrapText=" + wrapText + ", indent=" + indent + ", rotation=" + rotation
                + ", pattern=" + pattern() + '}';
    }

    public static final class Builder {

        private String fontName;
        private Integer fontSize;
        private Boolean bold;
        private Boolean italic;
        private Boolean underline;
        private Boolean strikeout;
        private String fontColor;
        private String foregroundColor;
        private String backgroundColor;
        private String borderColor;
        private BorderWeight border;
        private CellAlign horizontal;
        private VerticalAlign vertical;
        private Boolean wrapText;
        private Integer indent;
        private Integer rotation;
        private NumberPattern numberPattern;
        private String dataFormat;

        public Builder fontName(String value) {
            this.fontName = value;
            return this;
        }

        public Builder fontSize(Integer value) {
            if (value != null && (value < 1 || value > 409)) {
                throw new IllegalArgumentException("字号必须在 1 到 409 之间");
            }
            this.fontSize = value;
            return this;
        }

        public Builder bold(Boolean value) {
            this.bold = value;
            return this;
        }

        public Builder italic(Boolean value) {
            this.italic = value;
            return this;
        }

        public Builder underline(Boolean value) {
            this.underline = value;
            return this;
        }

        public Builder strikeout(Boolean value) {
            this.strikeout = value;
            return this;
        }

        public Builder fontColor(String value) {
            this.fontColor = value;
            return this;
        }

        public Builder foregroundColor(String value) {
            this.foregroundColor = value;
            return this;
        }

        public Builder backgroundColor(String value) {
            this.backgroundColor = value;
            return this;
        }

        public Builder borderColor(String value) {
            this.borderColor = value;
            return this;
        }

        public Builder border(BorderWeight value) {
            this.border = value;
            return this;
        }

        public Builder horizontal(CellAlign value) {
            this.horizontal = value;
            return this;
        }

        public Builder vertical(VerticalAlign value) {
            this.vertical = value;
            return this;
        }

        public Builder wrapText(Boolean value) {
            this.wrapText = value;
            return this;
        }

        public Builder indent(Integer value) {
            this.indent = value;
            return this;
        }

        public Builder rotation(Integer value) {
            if (value != null && (value < -90 || value > 90)) {
                throw new IllegalArgumentException("文字旋转角度必须在 -90 到 90 之间");
            }
            this.rotation = value;
            return this;
        }

        public Builder numberPattern(NumberPattern value) {
            this.numberPattern = value;
            return this;
        }

        public Builder dataFormat(String value) {
            this.dataFormat = value;
            return this;
        }

        public StyleSpec build() {
            return new StyleSpec(this);
        }
    }
}
