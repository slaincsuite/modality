package cn.slainc.excel.center;

import java.util.List;

public interface ExportDataProvider<T> {

    long total();

    List<T> fetch(long offset, int limit);
}
