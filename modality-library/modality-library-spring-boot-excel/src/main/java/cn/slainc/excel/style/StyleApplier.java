package cn.slainc.excel.style;

import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.Locale;

public final class StyleApplier {

    private StyleApplier() {
    }

    public static CellStyle apply(Workbook workbook, StyleSpec spec) {
        CellStyle style = workbook.createCellStyle();
        applyTo(style, workbook, spec);
        return style;
    }

    public static void applyTo(CellStyle style, Workbook workbook, StyleSpec spec) {
        if (style == null || workbook == null || spec == null || spec.isEmpty()) {
            return;
        }
        if (spec.hasFont()) {
            Font font = workbook.createFont();
            if (spec.getFontName() != null) {
                font.setFontName(spec.getFontName());
            }
            if (spec.getFontSize() != null) {
                font.setFontHeightInPoints(spec.getFontSize().shortValue());
            }
            if (spec.getBold() != null) {
                font.setBold(spec.getBold());
            }
            if (spec.getItalic() != null) {
                font.setItalic(spec.getItalic());
            }
            if (spec.getUnderline() != null) {
                font.setUnderline(spec.getUnderline() ? Font.U_SINGLE : Font.U_NONE);
            }
            if (spec.getStrikeout() != null) {
                font.setStrikeout(spec.getStrikeout());
            }
            if (spec.getFontColor() != null) {
                applyFontColor(workbook, font, spec.getFontColor());
            }
            style.setFont(font);
        }
        String pattern = spec.pattern();
        if (pattern != null) {
            style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(pattern));
        }
        if (spec.getForegroundColor() != null) {
            applyFillColor(workbook, style, spec.getForegroundColor(), true);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        if (spec.getBackgroundColor() != null) {
            applyFillColor(workbook, style, spec.getBackgroundColor(), false);
            if (spec.getForegroundColor() == null) {
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
        }
        if (spec.getBorder() != null && spec.getBorder().isVisible()) {
            BorderStyle border = borderStyle(spec.getBorder());
            style.setBorderTop(border);
            style.setBorderBottom(border);
            style.setBorderLeft(border);
            style.setBorderRight(border);
            if (spec.getBorderColor() != null) {
                applyBorderColor(workbook, style, spec.getBorderColor());
            }
        }
        if (spec.getHorizontal() != null && spec.getHorizontal() != CellAlign.GENERAL) {
            style.setAlignment(alignment(spec.getHorizontal()));
        }
        if (spec.getVertical() != null && spec.getVertical() != VerticalAlign.GENERAL) {
            style.setVerticalAlignment(verticalAlignment(spec.getVertical()));
        }
        if (spec.getWrapText() != null) {
            style.setWrapText(spec.getWrapText());
        }
        if (spec.getIndent() != null) {
            style.setIndention(spec.getIndent().shortValue());
        }
        if (spec.getRotation() != null) {
            style.setRotation(spec.getRotation().shortValue());
        }
    }

    public static XSSFColor xssfColor(String color) {
        java.awt.Color rgb = parse(color);
        return new XSSFColor(new byte[]{(byte) rgb.getRed(), (byte) rgb.getGreen(), (byte) rgb.getBlue()}, null);
    }

    public static short hssfIndex(Workbook workbook, String color) {
        java.awt.Color rgb = parse(color);
        if (!(workbook instanceof HSSFWorkbook hssfWorkbook)) {
            throw new IllegalArgumentException("仅 .xls 工作簿支持按索引取色，当前工作簿为 " + workbook.getClass().getName());
        }
        HSSFColor hssfColor = hssfWorkbook.getCustomPalette()
                .findSimilarColor(rgb.getRed(), rgb.getGreen(), rgb.getBlue());
        return hssfColor == null ? IndexedColors.AUTOMATIC.getIndex() : hssfColor.getIndex();
    }

    public static java.awt.Color parse(String color) {
        if (color == null || color.isBlank()) {
            throw new IllegalArgumentException("颜色值不能为空");
        }
        String text = color.trim();
        IndexedColors indexed = indexedByName(text);
        if (indexed != null) {
            HSSFColor hssfColor = HSSFColor.getIndexHash().get((int) indexed.getIndex());
            short[] rgb = hssfColor == null ? new short[]{0, 0, 0} : hssfColor.getTriplet();
            return new java.awt.Color(rgb[0], rgb[1], rgb[2]);
        }
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        if (text.length() == 3) {
            StringBuilder expanded = new StringBuilder(6);
            for (char value : text.toCharArray()) {
                expanded.append(value).append(value);
            }
            text = expanded.toString();
        }
        if (text.length() != 6) {
            throw new IllegalArgumentException("颜色值必须是 #RRGGBB 或标准颜色名: " + color);
        }
        try {
            int rgb = Integer.parseInt(text, 16);
            return new java.awt.Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("颜色值格式错误: " + color, ex);
        }
    }

    private static void applyFillColor(Workbook workbook, CellStyle style, String color, boolean foreground) {
        if (workbook instanceof HSSFWorkbook) {
            short index = hssfIndex(workbook, color);
            if (foreground) {
                style.setFillForegroundColor(index);
            } else {
                style.setFillBackgroundColor(index);
            }
            return;
        }
        XSSFColor xssfColor = xssfColor(color);
        if (foreground) {
            style.setFillForegroundColor(xssfColor);
        } else {
            style.setFillBackgroundColor(xssfColor);
        }
    }

    private static void applyFontColor(Workbook workbook, Font font, String color) {
        if (font instanceof XSSFFont xssfFont) {
            xssfFont.setColor(xssfColor(color));
            return;
        }
        font.setColor(hssfIndex(workbook, color));
    }

    private static void applyBorderColor(Workbook workbook, CellStyle style, String color) {
        if (style instanceof XSSFCellStyle xssfStyle) {
            XSSFColor xssfColor = xssfColor(color);
            xssfStyle.setTopBorderColor(xssfColor);
            xssfStyle.setBottomBorderColor(xssfColor);
            xssfStyle.setLeftBorderColor(xssfColor);
            xssfStyle.setRightBorderColor(xssfColor);
            return;
        }
        short index = hssfIndex(workbook, color);
        style.setTopBorderColor(index);
        style.setBottomBorderColor(index);
        style.setLeftBorderColor(index);
        style.setRightBorderColor(index);
    }

    private static IndexedColors indexedByName(String text) {
        try {
            return IndexedColors.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static BorderStyle borderStyle(BorderWeight weight) {
        return switch (weight) {
            case THIN -> BorderStyle.THIN;
            case MEDIUM -> BorderStyle.MEDIUM;
            case THICK -> BorderStyle.THICK;
            case DASHED -> BorderStyle.DASHED;
            case DOTTED -> BorderStyle.DOTTED;
            case DOUBLE -> BorderStyle.DOUBLE;
            default -> BorderStyle.NONE;
        };
    }

    private static HorizontalAlignment alignment(CellAlign align) {
        return switch (align) {
            case LEFT -> HorizontalAlignment.LEFT;
            case CENTER -> HorizontalAlignment.CENTER;
            case RIGHT -> HorizontalAlignment.RIGHT;
            case JUSTIFY -> HorizontalAlignment.JUSTIFY;
            case FILL -> HorizontalAlignment.FILL;
            default -> HorizontalAlignment.GENERAL;
        };
    }

    private static VerticalAlignment verticalAlignment(VerticalAlign align) {
        return switch (align) {
            case TOP -> VerticalAlignment.TOP;
            case CENTER -> VerticalAlignment.CENTER;
            case JUSTIFY -> VerticalAlignment.JUSTIFY;
            default -> VerticalAlignment.BOTTOM;
        };
    }
}
