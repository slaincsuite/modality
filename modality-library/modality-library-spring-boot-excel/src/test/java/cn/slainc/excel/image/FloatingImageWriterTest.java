package cn.slainc.excel.image;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingImageWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void addsFloatingImageWithAnchor() throws Exception {
        Path file = sheetFile("images.xlsx");
        byte[] thumbnail = Thumbnails.of(png(80, 40), 40, 40);

        FloatingImageWriter.add(file, 0, FloatingImage.of(thumbnail).withAnchor(ImageAnchor.of(5, 0, 10, 3)));

        assertEquals(1, FloatingImageWriter.pictureCount(file, 0));
        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            XSSFDrawing drawing = (XSSFDrawing) workbook.getSheetAt(0).getDrawingPatriarch();
            List<XSSFShape> shapes = drawing.getShapes();
            assertEquals(1, shapes.size());
            assertTrue(shapes.get(0) instanceof XSSFPicture);
            XSSFPicture picture = (XSSFPicture) shapes.get(0);
            XSSFClientAnchor anchor = (XSSFClientAnchor) picture.getAnchor();
            assertEquals(5, anchor.getRow1());
            assertEquals(10, anchor.getRow2());
            assertEquals(0, anchor.getCol1());
            assertEquals(3, anchor.getCol2());
            assertArrayEquals(thumbnail, picture.getPictureData().getData());
        }
    }

    @Test
    void generatesThumbnailKeepingAspectRatio() throws Exception {
        byte[] source = png(80, 40);
        byte[] thumbnail = Thumbnails.of(source, 40, 40);
        int[] size = Thumbnails.sizeOf(thumbnail);
        assertEquals(40, size[0]);
        assertEquals(20, size[1]);

        int[] original = Thumbnails.sizeOf(Thumbnails.of(source, 200, 200));
        assertEquals(80, original[0]);
        assertEquals(40, original[1]);

        int[] scaled = Thumbnails.sizeOf(Thumbnails.scale(source, 0.5d));
        assertEquals(40, scaled[0]);
        assertEquals(20, scaled[1]);

        assertThrows(IllegalArgumentException.class, () -> Thumbnails.of(source, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> Thumbnails.of(new byte[0], 10, 10));
        assertThrows(IllegalArgumentException.class, () -> Thumbnails.scale(source, 0));
    }

    @Test
    void addsMultipleFloatingImagesOnSecondSheet() throws Exception {
        Path file = sheetFile("multi.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
            workbook.createSheet("第二页");
            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }

        FloatingImageWriter.addAll(file, 1, List.of(FloatingImage.of(png(20, 20)).withAnchor(ImageAnchor.cell(1, 1)),
                FloatingImage.of(png(30, 30)).withFormat(PictureFormat.PNG).withAnchor(ImageAnchor.cell(4, 1))));

        assertEquals(2, FloatingImageWriter.pictureCount(file, 1));
        assertEquals(0, FloatingImageWriter.pictureCount(file, 0));
        assertThrows(IllegalArgumentException.class,
                () -> FloatingImageWriter.add(file, 9, FloatingImage.of(png(5, 5))));
    }

    private Path sheetFile(String name) throws IOException {
        Path file = tempDir.resolve(name);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("数据");
            sheet.createRow(0).createCell(0).setCellValue("标题");
            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }
        return file;
    }

    private byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }
}
