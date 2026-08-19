package cn.slainc.excel.watermark;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatermarkWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void appliesTiledTextWatermark() throws Exception {
        Path file = sheetFile("text-watermark.xlsx");

        WatermarkWriter.applyText(file, TextWatermark.of("内部资料").withRotation(-45).withAlphaPercent(30)
                .withTile(20, 4).withColor("#808080"));

        List<WatermarkShape> shapes = WatermarkWriter.shapes(file, 0);
        assertTrue(shapes.size() > 1, "水印应平铺出多个形状");
        for (WatermarkShape shape : shapes) {
            assertEquals(WatermarkKind.TEXT, shape.kind());
            assertEquals("内部资料", shape.text());
            assertEquals(-45, shape.rotation());
            assertEquals(30, shape.alphaPercent());
        }
        assertEquals(shapes.size(), WatermarkWriter.shapeCount(file, 0));
    }

    @Test
    void appliesImageWatermarkWithTransparency() throws Exception {
        Path file = sheetFile("image-watermark.xlsx");
        byte[] image = png(40, 20);

        WatermarkWriter.applyImage(file, ImageWatermark.of(image).withAlphaPercent(50).withTile(30, 8));

        List<WatermarkShape> shapes = WatermarkWriter.shapes(file, 0);
        assertFalse(shapes.isEmpty());
        for (WatermarkShape shape : shapes) {
            assertEquals(WatermarkKind.IMAGE, shape.kind());
            assertEquals(50, shape.alphaPercent());
            assertArrayEquals(image, shape.imageData());
        }
    }

    @Test
    void validatesWatermarkInput() {
        assertThrows(IllegalArgumentException.class, () -> TextWatermark.of(" "));
        assertThrows(IllegalArgumentException.class, () -> TextWatermark.of("水印").withAlphaPercent(101));
        assertThrows(IllegalArgumentException.class, () -> TextWatermark.of("水印").withRotation(120));
        assertThrows(IllegalArgumentException.class, () -> TextWatermark.of("水印").withTile(0, 1));
        assertThrows(IllegalArgumentException.class, () -> ImageWatermark.of(new byte[0]));
        assertEquals("宋体", TextWatermark.of("水印").fontName());
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
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }
}
