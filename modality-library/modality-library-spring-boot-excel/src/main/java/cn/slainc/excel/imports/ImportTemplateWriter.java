package cn.slainc.excel.imports;

import cn.slainc.excel.ExcelUtils;
import cn.slainc.excel.format.BeanHead;
import cn.slainc.excel.write.SheetOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class ImportTemplateWriter {

    public static final String DEFAULT_SHEET_NAME = "导入模板";

    private ImportTemplateWriter() {
    }

    public static void write(Path path, Class<?> head) {
        write(path, head, DEFAULT_SHEET_NAME, Map.of(), List.of());
    }

    public static <T> void write(Path path, Class<T> head, String sheetName, Map<Integer, List<String>> dropdowns,
                                 List<T> samples) {
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            write(outputStream, head, sheetName, dropdowns, samples);
        } catch (IOException ex) {
            throw new IllegalStateException("导出导入模板失败: " + path, ex);
        }
    }

    public static <T> void write(OutputStream outputStream, Class<T> head, String sheetName,
                                 Map<Integer, List<String>> dropdowns, List<T> samples) {
        ExcelUtils.write(outputStream, head, samples == null ? List.of() : samples,
                sheetName == null || sheetName.isBlank() ? DEFAULT_SHEET_NAME : sheetName, options(head, dropdowns));
    }

    public static <T> byte[] toBytes(Class<T> head, String sheetName, Map<Integer, List<String>> dropdowns,
                                     List<T> samples) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            write(outputStream, head, sheetName, dropdowns, samples);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("导出导入模板失败", ex);
        }
    }

    private static <T> SheetOptions options(Class<T> head, Map<Integer, List<String>> dropdowns) {
        BeanHead<T> beanHead = BeanHead.of(head);
        SheetOptions options = SheetOptions.defaults().freeze(1, 0);
        List<String> headers = beanHead.headers();
        for (int index = 0; index < headers.size(); index++) {
            options.columnWidth(index, Math.max(10, headers.get(index).length() * 2 + 6));
        }
        if (dropdowns != null) {
            dropdowns.forEach(options::dropdown);
        }
        return options;
    }
}
