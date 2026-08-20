package cn.slainc.excel.chart;

import cn.slainc.excel.support.WorkbookAccess;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFPieChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.nio.file.Path;

public final class ChartWriter {

    private ChartWriter() {
    }

    public static void add(Path file, ChartSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("图表定义不能为空");
        }
        WorkbookAccess.edit(file, workbook -> {
            XSSFWorkbook xssfWorkbook = WorkbookAccess.asXssf(workbook);
            if (spec.getSheetIndex() < 0 || spec.getSheetIndex() >= xssfWorkbook.getNumberOfSheets()) {
                throw new IllegalArgumentException("工作表下标越界: " + spec.getSheetIndex());
            }
            XSSFSheet sheet = xssfWorkbook.getSheetAt(spec.getSheetIndex());
            XSSFDrawing drawing = sheet.createDrawingPatriarch();
            XSSFClientAnchor anchor = new XSSFClientAnchor(0, 0, 0, 0, spec.getColumn1(), spec.getRow1(),
                    spec.getColumn2(), spec.getRow2());
            XSSFChart chart = drawing.createChart(anchor);
            chart.setTitleText(spec.getTitle());
            chart.setTitleOverlay(false);
            if (spec.getType() == ChartType.PIE) {
                XDDFChartData data = chart.createData(ChartTypes.PIE, null, null);
                ((XDDFPieChartData) data).setVaryColors(true);
                fill(sheet, chart, data, spec);
            } else {
                XDDFCategoryAxis categoryAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
                categoryAxis.setTitle("分类");
                XDDFValueAxis valueAxis = chart.createValueAxis(AxisPosition.LEFT);
                valueAxis.setCrosses(AxisCrosses.AUTO_ZERO);
                XDDFChartData data = chart.createData(
                        spec.getType() == ChartType.BAR ? ChartTypes.BAR : ChartTypes.LINE, categoryAxis, valueAxis);
                if (data instanceof XDDFBarChartData barChartData) {
                    barChartData.setBarDirection(BarDirection.COL);
                }
                fill(sheet, chart, data, spec);
            }
        });
    }

    public static int chartCount(Path file, int sheetIndex) {
        try (Workbook workbook = WorkbookAccess.open(file)) {
            if (!(workbook instanceof XSSFWorkbook xssfWorkbook)) {
                return 0;
            }
            XSSFDrawing drawing = xssfWorkbook.getSheetAt(sheetIndex).getDrawingPatriarch();
            return drawing == null ? 0 : drawing.getCharts().size();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("统计图表失败: " + file, ex);
        }
    }

    private static void fill(XSSFSheet sheet, XSSFChart chart, XDDFChartData data, ChartSpec spec) {
        for (ChartSeries series : spec.getSeries()) {
            XDDFChartData.Series chartSeries = data.addSeries(
                    XDDFDataSourcesFactory.fromStringCellRange(sheet, CellRangeAddress.valueOf(series.categoriesRange())),
                    XDDFDataSourcesFactory.fromNumericCellRange(sheet, CellRangeAddress.valueOf(series.valuesRange())));
            chartSeries.setTitle(series.title(), null);
        }
        chart.plot(data);
    }
}
