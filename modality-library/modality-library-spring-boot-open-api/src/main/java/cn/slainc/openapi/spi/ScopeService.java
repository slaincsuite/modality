package cn.slainc.openapi.spi;

import cn.slainc.openapi.domain.OpenScope;

import java.util.List;

public interface ScopeService {

    OpenScope register(OpenScope scope);

    OpenScope update(OpenScope scope);

    void remove(String name);

    OpenScope get(String name);

    List<OpenScope> list();

    List<OpenScope> listByGroup(String group);

    boolean requiresAudit(String scopeName);
}
