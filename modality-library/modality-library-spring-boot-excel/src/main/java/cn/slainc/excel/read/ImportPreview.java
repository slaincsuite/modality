package cn.slainc.excel.read;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ImportPreview {

    private ImportPreview() {
    }

    public static <T> List<T> preview(Path path, Class<T> head, int maxRows) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return preview(inputStream, head, maxRows);
        } catch (IOException ex) {
            throw new IllegalStateException("导入预览失败: " + path, ex);
        }
    }

    public static <T> List<T> preview(Path path, Class<T> head, int maxRows, int sheetNo) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            LimitedListener<T> listener = new LimitedListener<>(maxRows);
            FastExcel.read(inputStream, head, listener).sheet(sheetNo).doRead();
            return listener.rows();
        } catch (IOException ex) {
            throw new IllegalStateException("导入预览失败: " + path, ex);
        }
    }

    public static <T> List<T> preview(InputStream inputStream, Class<T> head, int maxRows) {
        LimitedListener<T> listener = new LimitedListener<>(maxRows);
        FastExcel.read(inputStream, head, listener).sheet().doRead();
        return listener.rows();
    }

    private static final class LimitedListener<T> implements ReadListener<T> {

        private final int maxRows;
        private final List<T> rows = new ArrayList<>();

        private LimitedListener(int maxRows) {
            this.maxRows = maxRows <= 0 ? 10 : maxRows;
        }

        @Override
        public void invoke(T data, AnalysisContext context) {
            rows.add(data);
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
        }

        @Override
        public boolean hasNext(AnalysisContext context) {
            return rows.size() < maxRows;
        }

        private List<T> rows() {
            return rows;
        }
    }
}
