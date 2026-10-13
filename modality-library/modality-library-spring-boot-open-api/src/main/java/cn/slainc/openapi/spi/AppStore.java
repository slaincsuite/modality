package cn.slainc.openapi.spi;

import cn.slainc.openapi.model.App;
import cn.slainc.openapi.model.AppStatus;

import java.util.List;
import java.util.Optional;

public interface AppStore {

    App save(App app);

    Optional<App> find(String appId);

    List<App> list();

    void delete(String appId);

    default boolean compareAndSetStatus(App app, AppStatus expectedStatus) {
        Optional<App> current = find(app.getAppId());
        if (current.isEmpty()) {
            return false;
        }
        // 内存实现可能把同一个实例交回来，此时已看不出旧状态，只能按最优努力放行；关系库实现须覆写为条件更新
        if (current.get() != app && current.get().getStatus() != expectedStatus) {
            return false;
        }
        save(app);
        return true;
    }
}
