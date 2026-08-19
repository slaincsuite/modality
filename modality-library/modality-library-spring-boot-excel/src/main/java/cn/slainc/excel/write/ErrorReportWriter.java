package cn.slainc.excel.write;

import cn.slainc.excel.read.ImportError;
import cn.slainc.excel.read.ImportResult;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ErrorReportWriter {

    public static final String DEFAULT_SHEET_NAME = "导入错误";

    private ErrorReportWriter() {
    }

    public static <T> void write(Path path, ImportResult<T> result) {
        write(path, result, DEFAULT_SHEET_NAME);
    }

    public static <T> void write(Path path, ImportResult<T> result, String sheetName) {
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            write(outputStream, result, sheetName);
        } catch (IOException ex) {
            throw new IllegalStateException("导出错误报告失败: " + path, ex);
        }
    }

    public static <T> void write(OutputStream outputStream, ImportResult<T> result, String sheetName) {
        List<ErrorReportRow> rows = toRows(result == null ? null : result.getErrors());
        if (rows.isEmpty()) {
            rows = List.of(new ErrorReportRow(null, null, null, "没有错误"));
        }
        SheetOptions options = SheetOptions.defaults()
                .freeze(1, 0)
                .autoFilter(4)
                .columnWidth(0, 10)
                .columnWidth(1, 20)
                .columnWidth(2, 24)
                .columnWidth(3, 48);
        cn.idev.excel.FastExcel.write(outputStream, ErrorReportRow.class)
                .sheet(sheetName == null ? DEFAULT_SHEET_NAME : sheetName)
                .registerWriteHandler(new SheetOptionHandler(options))
                .doWrite(rows);
    }

    public static List<ErrorReportRow> toRows(List<ImportError> errors) {
        List<ErrorReportRow> rows = new ArrayList<>();
        if (errors == null) {
            return rows;
        }
        for (ImportError error : errors) {
            rows.add(new ErrorReportRow(error.rowIndex(), error.field(),
                    error.value() == null ? null : String.valueOf(error.value()), error.message()));
        }
        return rows;
    }
}
