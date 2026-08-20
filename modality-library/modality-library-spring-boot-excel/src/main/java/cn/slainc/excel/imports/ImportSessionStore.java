package cn.slainc.excel.imports;

import java.nio.file.Path;
import java.util.List;

public interface ImportSessionStore {

    Path stage(String sessionId, Path source);

    void save(ImportSession<?> session);

    ImportSession<?> find(String sessionId);

    void remove(String sessionId);

    List<ImportSession<?>> list();

    int clearExpired();
}
