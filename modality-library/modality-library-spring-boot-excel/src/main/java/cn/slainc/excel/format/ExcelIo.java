package cn.slainc.excel.format;

import cn.slainc.excel.ExcelUtils;
import cn.slainc.excel.csv.CsvOptions;
import cn.slainc.excel.csv.CsvUtils;
import cn.slainc.excel.write.SheetOptions;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ExcelIo {

    private ExcelIo() {
    }

    public static ExcelFormat formatOf(Path path) {
        return ExcelFormat.of(path);
    }

    public static <T> void write(Path path, Class<T> head, List<T> data, String sheetName) {
        write(path, head, data, sheetName, null);
    }

    public static <T> void write(Path path, Class<T> head, List<T> data, String sheetName, SheetOptions options) {
        ExcelFormat format = requireSupported(path);
        switch (format) {
            case XLSX -> ExcelUtils.write(path, head, data, sheetName, options);
            case XLS -> LegacyWorkbookWriter.write(path, head, data, sheetName, options);
            case CSV -> writeCsv(path, head, data, CsvOptions.defaults());
            default -> throw new IllegalArgumentException("不支持的导出格式: " + path);
        }
    }

    public static <T> void writeCsv(Path path, Class<T> head, List<T> data, CsvOptions options) {
        BeanHead<T> beanHead = BeanHead.of(head);
        List<List<String>> rows = new ArrayList<>();
        if (data != null) {
            for (T bean : data) {
                rows.add(beanHead.valuesOf(bean));
            }
        }
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            CsvUtils.write(outputStream, beanHead.headers(), rows, options);
        } catch (IOException ex) {
            throw new IllegalStateException("导出 CSV 失败: " + path, ex);
        }
    }

    public static <T> List<T> read(Path path, Class<T> head) {
        return read(path, head, 0);
    }

    public static <T> List<T> read(Path path, Class<T> head, int sheetNo) {
        ExcelFormat format = requireSupported(path);
        switch (format) {
            case XLSX -> {
                return ExcelUtils.readSheet(path, head, sheetNo);
            }
            case XLS -> {
                return LegacyWorkbookReader.read(path, head, sheetNo);
            }
            case CSV -> {
                return readCsv(path, head, CsvOptions.defaults());
            }
            default -> throw new IllegalArgumentException("不支持的导入格式: " + path);
        }
    }

    public static <T> List<T> readCsv(Path path, Class<T> head, CsvOptions options) {
        BeanHead<T> beanHead = BeanHead.of(head);
        try (var inputStream = Files.newInputStream(path)) {
            List<List<String>> rows = CsvUtils.read(inputStream, options);
            if (rows.isEmpty()) {
                return List.of();
            }
            List<String> headers = rows.get(0);
            List<T> result = new ArrayList<>(Math.max(0, rows.size() - 1));
            for (int index = 1; index < rows.size(); index++) {
                result.add(beanHead.toBean(headers, rows.get(index)));
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("读取 CSV 失败: " + path, ex);
        }
    }

    public static <T> List<T> read(Path path, Class<T> head, String sheetName) {
        ExcelFormat format = requireSupported(path);
        if (format == ExcelFormat.XLSX) {
            return ExcelUtils.readSheet(path, head, sheetName);
        }
        return read(path, head, 0);
    }

    private static ExcelFormat requireSupported(Path path) {
        ExcelFormat format = ExcelFormat.of(path);
        if (format == ExcelFormat.UNKNOWN) {
            throw new IllegalArgumentException("无法识别文件格式: " + path + "，仅支持 .xlsx、.xls 与 .csv");
        }
        return format;
    }
}
