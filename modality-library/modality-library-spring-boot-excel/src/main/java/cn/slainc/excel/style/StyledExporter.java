package cn.slainc.excel.style;

import cn.idev.excel.FastExcel;
import cn.slainc.excel.format.BeanHead;
import cn.slainc.excel.support.WorkbookAccess;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StyledExporter {

    private StyledExporter() {
    }

    public static <T> void write(Path path, Class<T> head, List<T> data, StyledWriteOptions options) {
        StyledWriteOptions actual = options == null ? StyledWriteOptions.defaults() : options;
        FastExcel.write(path.toFile(), head).sheet(actual.getSheetName()).doWrite(data);
        WorkbookAccess.edit(path, workbook -> apply(workbook, head, actual));
    }

    public static <T> void write(OutputStream outputStream, Class<T> head, List<T> data, StyledWriteOptions options) {
        StyledWriteOptions actual = options == null ? StyledWriteOptions.defaults() : options;
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            FastExcel.write(buffer, head).sheet(actual.getSheetName()).doWrite(data);
            try (Workbook workbook = WorkbookAccess.open(buffer.toByteArray())) {
                apply(workbook, head, actual);
                workbook.write(outputStream);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("导出带样式的工作簿失败", ex);
        }
    }

    public static <T> byte[] writeToBytes(Class<T> head, List<T> data, StyledWriteOptions options) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            write(outputStream, head, data, options);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("导出带样式的工作簿失败", ex);
        }
    }

    static <T> void apply(Workbook workbook, Class<T> head, StyledWriteOptions options) {
        if (workbook.getNumberOfSheets() == 0) {
            return;
        }
        Sheet sheet = workbook.getSheetAt(0);
        Map<Integer, StyleSpec> columnStyles = columnStyles(head);
        Map<StyleSpec, CellStyle> cache = new HashMap<>();
        Row headerRow = sheet.getRow(0);
        if (headerRow != null && options.getHeaderStyle() != null) {
            for (int column = headerRow.getFirstCellNum(); column < headerRow.getLastCellNum(); column++) {
                Cell cell = headerRow.getCell(column);
                applyStyle(workbook, cell, options.getHeaderStyle(), cache);
            }
        }
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            StyleSpec rowStyle = rowIndex % 2 == 0 ? options.getStripeStyle() : options.getBodyStyle();
            for (int column = row.getFirstCellNum(); column < row.getLastCellNum(); column++) {
                StyleSpec spec = merge(columnStyles.get(column), rowStyle);
                applyStyle(workbook, row.getCell(column), spec, cache);
            }
            if (options.getRowHeight() != null) {
                row.setHeightInPoints(options.getRowHeight());
            }
        }
    }

    private static void applyStyle(Workbook workbook, Cell cell, StyleSpec spec, Map<StyleSpec, CellStyle> cache) {
        if (cell == null || spec == null || spec.isEmpty()) {
            return;
        }
        cell.setCellStyle(cache.computeIfAbsent(spec, value -> StyleApplier.apply(workbook, value)));
    }

    private static StyleSpec merge(StyleSpec columnStyle, StyleSpec rowStyle) {
        if (columnStyle == null) {
            return rowStyle;
        }
        return columnStyle.over(rowStyle);
    }

    private static <T> Map<Integer, StyleSpec> columnStyles(Class<T> head) {
        Map<Integer, StyleSpec> styles = new HashMap<>();
        for (BeanHead.Column column : BeanHead.of(head).columns()) {
            ExcelStyle annotation = column.field().getAnnotation(ExcelStyle.class);
            if (annotation != null) {
                styles.put(column.position(), StyleBuilder.from(annotation));
            }
        }
        return styles;
    }
}
