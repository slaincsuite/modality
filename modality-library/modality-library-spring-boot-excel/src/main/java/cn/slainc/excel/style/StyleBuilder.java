package cn.slainc.excel.style;

public final class StyleBuilder {

    private StyleBuilder() {
    }

    public static StyleSpec.Builder of() {
        return StyleSpec.builder();
    }

    public static StyleSpec header() {
        return StyleSpec.builder()
                .bold(true)
                .foregroundColor("#D9E1F2")
                .horizontal(CellAlign.CENTER)
                .vertical(VerticalAlign.CENTER)
                .border(BorderWeight.THIN)
                .build();
    }

    public static StyleSpec bold() {
        return StyleSpec.builder().bold(true).build();
    }

    public static StyleSpec centered() {
        return StyleSpec.builder().horizontal(CellAlign.CENTER).vertical(VerticalAlign.CENTER).build();
    }

    public static StyleSpec bordered() {
        return StyleSpec.builder().border(BorderWeight.THIN).build();
    }

    public static StyleSpec wrapped() {
        return StyleSpec.builder().wrapText(true).vertical(VerticalAlign.TOP).build();
    }

    public static StyleSpec money() {
        return StyleSpec.builder().numberPattern(NumberPattern.MONEY).horizontal(CellAlign.RIGHT).build();
    }

    public static StyleSpec percent() {
        return StyleSpec.builder().numberPattern(NumberPattern.PERCENT).horizontal(CellAlign.RIGHT).build();
    }

    public static StyleSpec warning() {
        return StyleSpec.builder()
                .fontColor("#9C0006")
                .foregroundColor("#FFC7CE")
                .bold(true)
                .border(BorderWeight.THIN)
                .build();
    }

    public static StyleSpec stripe() {
        return StyleSpec.builder().foregroundColor("#F2F2F2").build();
    }

    public static StyleSpec from(ExcelStyle annotation) {
        if (annotation == null) {
            return StyleSpec.empty();
        }
        return StyleSpec.builder()
                .fontName(blank(annotation.fontName()) ? null : annotation.fontName())
                .fontSize(annotation.fontSize() > 0 ? annotation.fontSize() : null)
                .bold(annotation.bold() ? Boolean.TRUE : null)
                .italic(annotation.italic() ? Boolean.TRUE : null)
                .underline(annotation.underline() ? Boolean.TRUE : null)
                .strikeout(annotation.strikeout() ? Boolean.TRUE : null)
                .fontColor(blank(annotation.fontColor()) ? null : annotation.fontColor())
                .foregroundColor(blank(annotation.foregroundColor()) ? null : annotation.foregroundColor())
                .backgroundColor(blank(annotation.backgroundColor()) ? null : annotation.backgroundColor())
                .borderColor(blank(annotation.borderColor()) ? null : annotation.borderColor())
                .border(annotation.border().isVisible() ? annotation.border() : null)
                .horizontal(annotation.horizontal() == CellAlign.GENERAL ? null : annotation.horizontal())
                .vertical(annotation.vertical() == VerticalAlign.GENERAL ? null : annotation.vertical())
                .wrapText(annotation.wrapText() ? Boolean.TRUE : null)
                .indent(annotation.indent() > 0 ? annotation.indent() : null)
                .rotation(annotation.rotation() != 0 ? annotation.rotation() : null)
                .numberPattern(annotation.numberPattern().isGeneral() ? null : annotation.numberPattern())
                .dataFormat(blank(annotation.dataFormat()) ? null : annotation.dataFormat())
                .build();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
