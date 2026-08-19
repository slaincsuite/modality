package cn.slainc.excel.watermark;

import cn.slainc.excel.style.StyleApplier;
import cn.slainc.excel.support.WorkbookAccess;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.TextHorizontalOverflow;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSimpleShape;
import org.apache.poi.xssf.usermodel.XSSFTextBox;
import org.apache.poi.xssf.usermodel.XSSFTextParagraph;
import org.apache.poi.xssf.usermodel.XSSFTextRun;
import org.openxmlformats.schemas.drawingml.x2006.main.CTBlip;
import org.openxmlformats.schemas.drawingml.x2006.main.CTShapeProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTSolidColorFillProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTSRgbColor;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextCharacterProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTransform2D;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTPicture;

import java.nio.file.Path;
import java.util.List;

public final class WatermarkWriter {

    private static final int TILE_AREA_ROWS = 60;
    private static final int TILE_AREA_COLUMNS = 16;

    private WatermarkWriter() {
    }

    public static void applyText(Path file, TextWatermark watermark) {
        applyText(file, -1, watermark);
    }

    public static void applyText(Path file, int sheetIndex, TextWatermark watermark) {
        WorkbookAccess.edit(file, workbook -> {
            for (Sheet sheet : sheets(workbook, sheetIndex)) {
                XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
                for (int row = 0; row < TILE_AREA_ROWS; row += watermark.tileRows()) {
                    for (int column = 0; column < TILE_AREA_COLUMNS; column += watermark.tileColumns()) {
                        drawText(drawing, watermark, row, column);
                    }
                }
            }
        });
    }

    public static void applyImage(Path file, ImageWatermark watermark) {
        applyImage(file, -1, watermark);
    }

    public static void applyImage(Path file, int sheetIndex, ImageWatermark watermark) {
        WorkbookAccess.edit(file, workbook -> {
            for (Sheet sheet : sheets(workbook, sheetIndex)) {
                XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
                int pictureIndex = workbook.addPicture(watermark.data(), watermark.format().pictureType());
                for (int row = 0; row < TILE_AREA_ROWS; row += watermark.tileRows()) {
                    for (int column = 0; column < TILE_AREA_COLUMNS; column += watermark.tileColumns()) {
                        drawPicture(drawing, pictureIndex, watermark, row, column);
                    }
                }
            }
        });
    }

    public static int shapeCount(Path file, int sheetIndex) {
        try (Workbook workbook = WorkbookAccess.open(file)) {
            XSSFDrawing drawing = (XSSFDrawing) sheets(workbook, sheetIndex).get(0).getDrawingPatriarch();
            return drawing == null ? 0 : drawing.getShapes().size();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("统计水印形状失败: " + file, ex);
        }
    }

    public static List<WatermarkShape> shapes(Path file, int sheetIndex) {
        try (Workbook workbook = WorkbookAccess.open(file)) {
            XSSFDrawing drawing = (XSSFDrawing) sheets(workbook, sheetIndex).get(0).getDrawingPatriarch();
            if (drawing == null) {
                return List.of();
            }
            List<WatermarkShape> shapes = new java.util.ArrayList<>();
            for (XSSFShape shape : drawing.getShapes()) {
                if (shape instanceof XSSFPicture picture) {
                    shapes.add(WatermarkShape.image(picture.getPictureData().getData(), rotation(shape),
                            pictureAlpha(picture)));
                } else if (shape instanceof XSSFSimpleShape simpleShape) {
                    shapes.add(WatermarkShape.text(simpleShape.getText(), rotation(shape), textAlpha(simpleShape)));
                }
            }
            return shapes;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("读取水印形状失败: " + file, ex);
        }
    }

    private static int rotation(XSSFShape shape) {
        if (!(shape instanceof XSSFSimpleShape simpleShape)) {
            return 0;
        }
        CTShapeProperties properties = simpleShape.getCTShape().getSpPr();
        if (properties == null || !properties.isSetXfrm()) {
            return 0;
        }
        return properties.getXfrm().getRot() / 60_000;
    }

    private static int pictureAlpha(XSSFPicture picture) {
        CTBlip blip = picture.getCTPicture().getBlipFill().getBlip();
        if (blip.sizeOfAlphaModFixArray() == 0) {
            return 100;
        }
        return alphaPercent(blip.getAlphaModFixArray(0).getAmt());
    }

    private static int textAlpha(XSSFSimpleShape shape) {
        List<XSSFTextParagraph> paragraphs = shape.getTextParagraphs();
        if (paragraphs.isEmpty() || paragraphs.get(0).getTextRuns().isEmpty()) {
            return 100;
        }
        CTTextCharacterProperties properties = paragraphs.get(0).getTextRuns().get(0).getXmlObject().getRPr();
        if (properties == null || !properties.isSetSolidFill() || !properties.getSolidFill().isSetSrgbClr()) {
            return 100;
        }
        CTSRgbColor color = properties.getSolidFill().getSrgbClr();
        if (color.sizeOfAlphaArray() == 0) {
            return 100;
        }
        return alphaPercent(color.getAlphaArray(0).getVal());
    }

    private static int alphaPercent(Object value) {
        if (value instanceof Number number) {
            return number.intValue() / 1_000;
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) {
            return 100;
        }
        if (text.endsWith("%")) {
            return (int) Math.round(Double.parseDouble(text.substring(0, text.length() - 1)));
        }
        return (int) Math.round(Double.parseDouble(text)) / 1_000;
    }

    private static void drawText(XSSFDrawing drawing, TextWatermark watermark, int row, int column) {
        XSSFTextBox textBox = drawing.createTextbox(anchor(row, column, watermark.tileRows(), watermark.tileColumns()));
        textBox.setText(watermark.text());
        textBox.setTextHorizontalOverflow(TextHorizontalOverflow.OVERFLOW);
        textBox.setNoFill(true);
        CTShapeProperties properties = textBox.getCTShape().getSpPr();
        properties.addNewLn().addNewNoFill();
        CTTransform2D transform = properties.isSetXfrm() ? properties.getXfrm() : properties.addNewXfrm();
        transform.setRot(watermark.rotation() * 60_000);
        applyRunStyle(textBox, watermark);
    }

    private static void applyRunStyle(XSSFTextBox textBox, TextWatermark watermark) {
        List<XSSFTextParagraph> paragraphs = textBox.getTextParagraphs();
        if (paragraphs.isEmpty()) {
            return;
        }
        XSSFTextParagraph paragraph = paragraphs.get(0);
        List<XSSFTextRun> runs = paragraph.getTextRuns();
        XSSFTextRun run = runs.isEmpty() ? paragraph.addNewTextRun() : runs.get(0);
        run.setFont(watermark.fontName());
        run.setFontSize(watermark.fontSize());
        java.awt.Color color = StyleApplier.parse(watermark.color());
        run.setFontColor(color);
        applyTextAlpha(run, color, watermark.alphaPercent());
    }

    private static void applyTextAlpha(XSSFTextRun run, java.awt.Color color, int alphaPercent) {
        CTTextCharacterProperties properties = run.getXmlObject().getRPr();
        if (properties == null) {
            properties = run.getXmlObject().addNewRPr();
        }
        CTSolidColorFillProperties fill = properties.isSetSolidFill()
                ? properties.getSolidFill()
                : properties.addNewSolidFill();
        CTSRgbColor srgb = fill.isSetSrgbClr() ? fill.getSrgbClr() : fill.addNewSrgbClr();
        if (srgb.getVal() == null) {
            srgb.setVal(new byte[]{(byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue()});
        }
        srgb.addNewAlpha().setVal(alphaPercent * 1_000);
    }

    private static void drawPicture(XSSFDrawing drawing, int pictureIndex, ImageWatermark watermark, int row,
                                    int column) {
        XSSFPicture picture = drawing.createPicture(anchor(row, column, watermark.tileRows(), watermark.tileColumns()),
                pictureIndex);
        CTPicture ctPicture = picture.getCTPicture();
        ctPicture.getBlipFill().getBlip().addNewAlphaModFix().setAmt(watermark.alphaPercent() * 1_000);
    }

    private static XSSFClientAnchor anchor(int row, int column, int tileRows, int tileColumns) {
        return new XSSFClientAnchor(0, 0, 0, 0, column, row, column + tileColumns, row + tileRows);
    }

    private static List<Sheet> sheets(Workbook workbook, int sheetIndex) {
        if (sheetIndex < 0) {
            List<Sheet> sheets = new java.util.ArrayList<>();
            for (Sheet sheet : workbook) {
                sheets.add(sheet);
            }
            return sheets;
        }
        if (sheetIndex >= workbook.getNumberOfSheets()) {
            throw new IllegalArgumentException("工作表下标越界: " + sheetIndex);
        }
        return List.of(workbook.getSheetAt(sheetIndex));
    }
}
