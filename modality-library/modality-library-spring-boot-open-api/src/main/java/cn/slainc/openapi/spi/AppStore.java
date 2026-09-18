package cn.slainc.openapi.spi;

import cn.slainc.openapi.model.App;

import java.util.List;
import java.util.Optional;

public interface AppStore {

    App save(App app);

    Optional<App> find(String appId);

    List<App> list();

    void delete(String appId);
}
