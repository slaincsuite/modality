package cn.slainc.openapi.spi;

import cn.slainc.openapi.domain.App;
import cn.slainc.openapi.domain.AppAction;
import cn.slainc.openapi.domain.AppStatus;

import java.util.List;

public interface AppService {

    App create(App app);

    App update(App app);

    App get(String appId);

    List<App> listByOwner(String ownerId);

    List<App> search(String keyword, String category, String tag);

    App transition(String appId, AppAction action);

    default App submit(String appId) {
        return transition(appId, AppAction.SUBMIT);
    }

    default App approve(String appId) {
        return transition(appId, AppAction.APPROVE);
    }

    default App reject(String appId) {
        return transition(appId, AppAction.REJECT);
    }

    default App offline(String appId) {
        return transition(appId, AppAction.OFFLINE);
    }

    default App republish(String appId) {
        return transition(appId, AppAction.REPUBLISH);
    }

    List<App> listByStatus(AppStatus status);
}
