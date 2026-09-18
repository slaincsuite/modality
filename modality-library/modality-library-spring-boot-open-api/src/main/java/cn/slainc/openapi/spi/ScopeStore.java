package cn.slainc.openapi.spi;

import cn.slainc.openapi.model.OpenScope;

import java.util.List;
import java.util.Optional;

public interface ScopeStore {

    OpenScope save(OpenScope scope);

    Optional<OpenScope> find(String name);

    List<OpenScope> list();

    void remove(String name);
}
