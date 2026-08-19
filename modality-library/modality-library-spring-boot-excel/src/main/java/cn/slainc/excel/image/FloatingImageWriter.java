package cn.slainc.excel.image;

import cn.slainc.excel.support.WorkbookAccess;
import org.apache.poi.hssf.usermodel.HSSFClientAnchor;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.nio.file.Path;
import java.util.List;

public final class FloatingImageWriter {

    private FloatingImageWriter() {
    }

    public static void add(Path file, FloatingImage image) {
        add(file, 0, image);
    }

    public static void add(Path file, int sheetIndex, FloatingImage image) {
        addAll(file, sheetIndex, List.of(image));
    }

    public static void addAll(Path file, int sheetIndex, List<FloatingImage> images) {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("至少需要一张浮动图片");
        }
        WorkbookAccess.edit(file, workbook -> {
            Sheet sheet = sheetAt(workbook, sheetIndex);
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            for (FloatingImage image : images) {
                int pictureIndex = workbook.addPicture(image.data(), image.format().pictureType());
                drawing.createPicture(clientAnchor(workbook, image.anchor()), pictureIndex);
            }
        });
    }

    public static int pictureCount(Path file, int sheetIndex) {
        try (Workbook workbook = WorkbookAccess.open(file)) {
            if (!(workbook instanceof XSSFWorkbook)) {
                return 0;
            }
            Drawing<?> drawing = sheetAt(workbook, sheetIndex).getDrawingPatriarch();
            if (!(drawing instanceof XSSFDrawing xssfDrawing)) {
                return 0;
            }
            int count = 0;
            for (XSSFShape shape : xssfDrawing.getShapes()) {
                if (shape instanceof XSSFPicture) {
                    count++;
                }
            }
            return count;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("统计浮动图片失败: " + file, ex);
        }
    }

    private static ClientAnchor clientAnchor(Workbook workbook, ImageAnchor anchor) {
        if (workbook instanceof XSSFWorkbook) {
            return new XSSFClientAnchor(anchor.dx1(), anchor.dy1(), anchor.dx2(), anchor.dy2(), anchor.column1(),
                    anchor.row1(), anchor.column2(), anchor.row2());
        }
        return new HSSFClientAnchor(anchor.dx1(), anchor.dy1(), anchor.dx2(), anchor.dy2(), (short) anchor.column1(),
                anchor.row1(), (short) anchor.column2(), anchor.row2());
    }

    private static Sheet sheetAt(Workbook workbook, int sheetIndex) {
        if (sheetIndex < 0 || sheetIndex >= workbook.getNumberOfSheets()) {
            throw new IllegalArgumentException("工作表下标越界: " + sheetIndex);
        }
        return workbook.getSheetAt(sheetIndex);
    }
}
