package cn.slainc.excel;

import cn.idev.excel.ExcelWriter;
import cn.idev.excel.FastExcel;
import cn.idev.excel.write.metadata.WriteSheet;
import cn.slainc.excel.read.BatchReadListener;
import cn.slainc.excel.read.ImportPreview;
import cn.slainc.excel.read.ImportResult;
import cn.slainc.excel.read.ReadOptions;
import cn.slainc.excel.write.SheetOptionHandler;
import cn.slainc.excel.write.SheetOptions;
import jakarta.validation.Validator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ExcelUtils {

    private ExcelUtils() {
    }

    public static <T> void write(OutputStream outputStream, Class<T> head, List<T> data) {
        write(outputStream, head, data, null);
    }

    public static <T> void write(OutputStream outputStream, Class<T> head, List<T> data, String sheetName) {
        FastExcel.write(outputStream, head).sheet(sheetName).doWrite(data);
    }

    public static <T> void write(OutputStream outputStream, Class<T> head, List<T> data, String sheetName,
                                 SheetOptions options) {
        var sheetBuilder = FastExcel.write(outputStream, head).sheet(sheetName);
        if (options != null && !options.isEmpty()) {
            sheetBuilder.registerWriteHandler(new SheetOptionHandler(options));
        }
        sheetBuilder.doWrite(data);
    }

    public static <T> void write(Path path, Class<T> head, List<T> data, String sheetName, SheetOptions options) {
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            write(outputStream, head, data, sheetName, options);
        } catch (IOException ex) {
            throw new IllegalStateException("导出 Excel 失败: " + path, ex);
        }
    }

    public static <T> List<T> preview(Path path, Class<T> head, int maxRows) {
        return ImportPreview.preview(path, head, maxRows);
    }

    public static <T> List<T> preview(Path path, Class<T> head, int maxRows, int sheetNo) {
        return ImportPreview.preview(path, head, maxRows, sheetNo);
    }

    public static <T> void write(Path path, Class<T> head, List<T> data) {
        write(path, head, data, null);
    }

    public static <T> void write(Path path, Class<T> head, List<T> data, String sheetName) {
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            write(outputStream, head, data, sheetName);
        } catch (IOException ex) {
            throw new IllegalStateException("导出 Excel 失败: " + path, ex);
        }
    }

    public static void writeMultiSheet(Path path, Map<String, SheetData<?>> sheets) {
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            writeMultiSheet(outputStream, sheets);
        } catch (IOException ex) {
            throw new IllegalStateException("导出 Excel 失败: " + path, ex);
        }
    }

    public static void writeMultiSheet(OutputStream outputStream, Map<String, SheetData<?>> sheets) {
        if (sheets == null || sheets.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个 Sheet");
        }
        ExcelWriter writer = FastExcel.write(outputStream).build();
        try {
            int index = 0;
            for (Map.Entry<String, SheetData<?>> entry : sheets.entrySet()) {
                writeSheet(writer, index++, entry.getKey(), entry.getValue());
            }
        } finally {
            writer.finish();
        }
    }

    private static <T> void writeSheet(ExcelWriter writer, int index, String name, SheetData<T> sheetData) {
        WriteSheet sheet = FastExcel.writerSheet(index, name).head(sheetData.head()).build();
        writer.write(sheetData.data(), sheet);
    }

    public static <T> List<T> read(InputStream inputStream, Class<T> head) {
        return FastExcel.read(inputStream, head, null).sheet().doReadSync();
    }

    public static <T> List<T> read(Path path, Class<T> head) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return read(inputStream, head);
        } catch (IOException ex) {
            throw new IllegalStateException("读取 Excel 失败: " + path, ex);
        }
    }

    public static <T> List<T> readSheet(Path path, Class<T> head, int sheetNo) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return FastExcel.read(inputStream, head, null).sheet(sheetNo).doReadSync();
        } catch (IOException ex) {
            throw new IllegalStateException("读取 Excel 失败: " + path, ex);
        }
    }

    public static <T> List<T> readSheet(Path path, Class<T> head, String sheetName) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return FastExcel.read(inputStream, head, null).sheet(sheetName).doReadSync();
        } catch (IOException ex) {
            throw new IllegalStateException("读取 Excel 失败: " + path, ex);
        }
    }

    public static <T> ImportResult<T> read(InputStream inputStream, Class<T> head, ReadOptions options,
                                           Validator validator) {
        ReadOptions actual = options == null ? ReadOptions.defaults() : options;
        BatchReadListener<T> listener = new BatchReadListener<>(actual, validator, batchConsumer(actual));
        var builder = FastExcel.read(inputStream, head, listener).headRowNumber(actual.headRowNumber());
        if (actual.ignoreEmptyRow()) {
            builder.ignoreEmptyRow(true);
        }
        var sheetBuilder = actual.sheetName() != null ? builder.sheet(actual.sheetName())
                : actual.sheetNo() != null ? builder.sheet(actual.sheetNo()) : builder.sheet();
        sheetBuilder.doRead();
        return listener.getResult();
    }

    @SuppressWarnings("unchecked")
    private static <T> Consumer<List<T>> batchConsumer(ReadOptions options) {
        return (Consumer<List<T>>) (Consumer<?>) options.batchConsumer();
    }

    public static <T> ImportResult<T> read(Path path, Class<T> head, ReadOptions options, Validator validator) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return read(inputStream, head, options, validator);
        } catch (IOException ex) {
            throw new IllegalStateException("读取 Excel 失败: " + path, ex);
        }
    }

    public static <T> ImportResult<T> readWithResult(Path path, Class<T> head) {
        return read(path, head, ReadOptions.defaults(), null);
    }

    public static Map<String, SheetData<?>> sheets() {
        return new LinkedHashMap<>();
    }

    public record SheetData<T>(Class<T> head, List<T> data) {

        public static <T> SheetData<T> of(Class<T> head, List<T> data) {
            return new SheetData<>(head, data);
        }
    }
}
