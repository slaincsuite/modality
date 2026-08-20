package cn.slainc.excel.imports;

import java.util.List;

public interface ImportLogStore {

    void save(ImportLogEntry entry);

    List<ImportLogEntry> list(int limit);

    int size();

    void clear();
}
