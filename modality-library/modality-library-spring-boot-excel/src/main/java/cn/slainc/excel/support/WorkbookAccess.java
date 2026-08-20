package cn.slainc.excel.support;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

public final class WorkbookAccess {

    private WorkbookAccess() {
    }

    public static Workbook open(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return WorkbookFactory.create(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("打开工作簿失败: " + path, ex);
        }
    }

    public static Workbook open(byte[] bytes) {
        try {
            return WorkbookFactory.create(new ByteArrayInputStream(bytes));
        } catch (IOException ex) {
            throw new IllegalStateException("解析工作簿失败", ex);
        }
    }

    public static XSSFWorkbook asXssf(Workbook workbook) {
        if (workbook instanceof XSSFWorkbook xssfWorkbook) {
            return xssfWorkbook;
        }
        throw new IllegalArgumentException("该能力仅支持 .xlsx 工作簿");
    }

    public static void edit(Path path, Consumer<Workbook> editor) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("文件不存在: " + path);
        }
        try (Workbook workbook = open(path)) {
            editor.accept(workbook);
            write(workbook, path);
        } catch (IOException ex) {
            throw new IllegalStateException("保存工作簿失败: " + path, ex);
        }
    }

    public static void write(Workbook workbook, Path path) {
        Path directory = path.toAbsolutePath().getParent();
        Path temp;
        try {
            temp = Files.createTempFile(directory, "excel-", ".tmp");
            try (OutputStream outputStream = Files.newOutputStream(temp)) {
                workbook.write(outputStream);
            }
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("写出工作簿失败: " + path, ex);
        }
    }

    public static byte[] bytes(Workbook workbook) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("序列化工作簿失败", ex);
        }
    }

    public static byte[] readAll(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new IllegalStateException("读取文件失败: " + path, ex);
        }
    }

    public static void close(Workbook workbook) {
        if (workbook == null) {
            return;
        }
        try {
            workbook.close();
        } catch (IOException ex) {
            throw new IllegalStateException("关闭工作簿失败", ex);
        }
    }
}
