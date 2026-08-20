package cn.slainc.excel.chart;

import cn.slainc.excel.support.WorkbookAccess;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.DataConsolidateFunction;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFPivotTable;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.nio.file.Path;

public final class PivotWriter {

    private PivotWriter() {
    }

    public static void add(Path file, PivotSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("透视表定义不能为空");
        }
        WorkbookAccess.edit(file, workbook -> {
            XSSFWorkbook xssfWorkbook = WorkbookAccess.asXssf(workbook);
            if (spec.getSheetIndex() < 0 || spec.getSheetIndex() >= xssfWorkbook.getNumberOfSheets()) {
                throw new IllegalArgumentException("工作表下标越界: " + spec.getSheetIndex());
            }
            XSSFSheet sheet = xssfWorkbook.getSheetAt(spec.getSheetIndex());
            AreaReference source = new AreaReference(spec.getSourceRange(), SpreadsheetVersion.EXCEL2007);
            CellReference position = new CellReference(spec.getPosition());
            XSSFPivotTable pivotTable = sheet.createPivotTable(source, position, sheet);
            for (Integer columnIndex : spec.getRowLabels()) {
                pivotTable.addRowLabel(columnIndex);
            }
            for (Integer columnIndex : spec.getColumnLabels()) {
                pivotTable.addColLabel(columnIndex);
            }
            for (PivotSpec.DataColumn dataColumn : spec.getDataColumns()) {
                pivotTable.addColumnLabel(function(dataColumn.function()), dataColumn.columnIndex(), dataColumn.name());
            }
        });
    }

    public static int pivotTableCount(Path file) {
        try (Workbook workbook = WorkbookAccess.open(file)) {
            if (!(workbook instanceof XSSFWorkbook xssfWorkbook)) {
                return 0;
            }
            return xssfWorkbook.getPivotTables().size();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("统计透视表失败: " + file, ex);
        }
    }

    public static boolean hasPivotPart(Path file) {
        try (Workbook workbook = WorkbookAccess.open(file)) {
            if (!(workbook instanceof XSSFWorkbook xssfWorkbook)) {
                return false;
            }
            return xssfWorkbook.getPackage().getParts().stream()
                    .anyMatch(part -> part.getPartName().getName().contains("pivotTables/"));
        } catch (java.io.IOException | org.apache.poi.openxml4j.exceptions.InvalidFormatException ex) {
            throw new IllegalStateException("检查透视表部件失败: " + file, ex);
        }
    }

    private static DataConsolidateFunction function(PivotFunction function) {
        return switch (function) {
            case SUM -> DataConsolidateFunction.SUM;
            case COUNT -> DataConsolidateFunction.COUNT;
            case AVERAGE -> DataConsolidateFunction.AVERAGE;
            case MAX -> DataConsolidateFunction.MAX;
            case MIN -> DataConsolidateFunction.MIN;
        };
    }
}
