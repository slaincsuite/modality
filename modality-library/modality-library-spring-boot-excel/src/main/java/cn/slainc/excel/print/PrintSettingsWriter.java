package cn.slainc.excel.print;

import cn.slainc.excel.support.WorkbookAccess;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;

import java.nio.file.Path;

public final class PrintSettingsWriter {

    private PrintSettingsWriter() {
    }

    public static void apply(Path file, PrintSettings settings) {
        apply(file, 0, settings);
    }

    public static void apply(Path file, int sheetIndex, PrintSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("打印设置不能为空");
        }
        WorkbookAccess.edit(file, workbook -> {
            if (sheetIndex < 0 || sheetIndex >= workbook.getNumberOfSheets()) {
                throw new IllegalArgumentException("工作表下标越界: " + sheetIndex);
            }
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            PrintSetup printSetup = sheet.getPrintSetup();
            printSetup.setPaperSize(settings.getPaperSize().code());
            printSetup.setLandscape(settings.getOrientation().landscape());
            if (settings.getScale() != null) {
                printSetup.setScale(settings.getScale());
            }
            if (settings.getFitToWidth() != null) {
                sheet.setFitToPage(true);
                printSetup.setFitWidth(settings.getFitToWidth());
                printSetup.setFitHeight(settings.getFitToHeight());
            }
            if (settings.getPrintArea() != null) {
                workbook.setPrintArea(sheetIndex, settings.getPrintArea());
            }
            if (settings.getRepeatTitleRowFrom() != null) {
                sheet.setRepeatingRows(new CellRangeAddress(settings.getRepeatTitleRowFrom(),
                        settings.getRepeatTitleRowTo(), -1, -1));
            }
            if (settings.getRepeatTitleColumnFrom() != null) {
                sheet.setRepeatingColumns(new CellRangeAddress(-1, -1, settings.getRepeatTitleColumnFrom(),
                        settings.getRepeatTitleColumnTo()));
            }
            if (settings.getHeaderLeft() != null) {
                sheet.getHeader().setLeft(settings.getHeaderLeft());
            }
            if (settings.getHeaderCenter() != null) {
                sheet.getHeader().setCenter(settings.getHeaderCenter());
            }
            if (settings.getHeaderRight() != null) {
                sheet.getHeader().setRight(settings.getHeaderRight());
            }
            if (settings.getFooterLeft() != null) {
                sheet.getFooter().setLeft(settings.getFooterLeft());
            }
            if (settings.getFooterCenter() != null) {
                sheet.getFooter().setCenter(settings.getFooterCenter());
            }
            if (settings.getFooterRight() != null) {
                sheet.getFooter().setRight(settings.getFooterRight());
            }
            for (Integer rowBreak : settings.getRowBreaks()) {
                sheet.setRowBreak(rowBreak);
            }
            for (Integer columnBreak : settings.getColumnBreaks()) {
                sheet.setColumnBreak(columnBreak);
            }
            if (settings.getMarginLeft() != null) {
                sheet.setMargin(PageMargin.LEFT, settings.getMarginLeft());
            }
            if (settings.getMarginRight() != null) {
                sheet.setMargin(PageMargin.RIGHT, settings.getMarginRight());
            }
            if (settings.getMarginTop() != null) {
                sheet.setMargin(PageMargin.TOP, settings.getMarginTop());
            }
            if (settings.getMarginBottom() != null) {
                sheet.setMargin(PageMargin.BOTTOM, settings.getMarginBottom());
            }
            if (settings.getPrintGridlines() != null) {
                sheet.setPrintGridlines(settings.getPrintGridlines());
            }
            if (settings.getHorizontallyCenter() != null) {
                sheet.setHorizontallyCenter(settings.getHorizontallyCenter());
            }
            if (settings.getVerticallyCenter() != null) {
                sheet.setVerticallyCenter(settings.getVerticallyCenter());
            }
        });
    }
}
