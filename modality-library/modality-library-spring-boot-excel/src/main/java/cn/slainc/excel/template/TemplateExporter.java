package cn.slainc.excel.template;

import cn.idev.excel.ExcelWriter;
import cn.idev.excel.FastExcel;
import cn.idev.excel.write.metadata.WriteSheet;
import cn.idev.excel.write.metadata.fill.FillConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class TemplateExporter {

    private TemplateExporter() {
    }

    public static void fill(InputStream template, OutputStream outputStream, Map<String, Object> variables) {
        fill(template, outputStream, variables, null);
    }

    public static void fill(InputStream template, OutputStream outputStream, Map<String, Object> variables,
                            List<?> rows) {
        try (ExcelWriter writer = FastExcel.write(outputStream).withTemplate(template).build()) {
            WriteSheet sheet = FastExcel.writerSheet().build();
            if (rows != null && !rows.isEmpty()) {
                writer.fill(rows, FillConfig.builder().forceNewRow(Boolean.TRUE).build(), sheet);
            }
            if (variables != null && !variables.isEmpty()) {
                writer.fill(variables, sheet);
            }
        }
    }

    public static void fill(Path template, Path output, Map<String, Object> variables, List<?> rows) {
        try (InputStream inputStream = Files.newInputStream(template);
             OutputStream outputStream = Files.newOutputStream(output)) {
            fill(inputStream, outputStream, variables, rows);
        } catch (IOException ex) {
            throw new IllegalStateException("按模板导出失败: " + template, ex);
        }
    }

    public static void fill(File template, File output, Map<String, Object> variables, List<?> rows) {
        fill(template.toPath(), output.toPath(), variables, rows);
    }

    public static void fillMultiple(Path template, Path output, List<TemplateSheetFill> fills) {
        try (InputStream inputStream = Files.newInputStream(template);
             OutputStream outputStream = Files.newOutputStream(output);
             ExcelWriter writer = FastExcel.write(outputStream).withTemplate(inputStream).build()) {
            for (TemplateSheetFill fill : fills) {
                WriteSheet sheet = fill.sheetName() == null
                        ? FastExcel.writerSheet(fill.sheetNo()).build()
                        : FastExcel.writerSheet(fill.sheetNo(), fill.sheetName()).build();
                if (fill.rows() != null && !fill.rows().isEmpty()) {
                    writer.fill(fill.rows(), FillConfig.builder().forceNewRow(Boolean.TRUE).build(), sheet);
                }
                if (fill.variables() != null && !fill.variables().isEmpty()) {
                    writer.fill(fill.variables(), sheet);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("按模板导出多 Sheet 失败: " + template, ex);
        }
    }

    public record TemplateSheetFill(int sheetNo, String sheetName, Map<String, Object> variables, List<?> rows) {

        public static TemplateSheetFill of(int sheetNo, String sheetName, Map<String, Object> variables,
                                           List<?> rows) {
            return new TemplateSheetFill(sheetNo, sheetName, variables, rows);
        }
    }
}
