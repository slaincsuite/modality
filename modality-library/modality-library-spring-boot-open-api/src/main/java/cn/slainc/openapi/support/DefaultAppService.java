package cn.slainc.openapi.support;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.openapi.event.AppApprovedEvent;
import cn.slainc.openapi.event.AppCreatedEvent;
import cn.slainc.openapi.model.App;
import cn.slainc.openapi.model.AppAction;
import cn.slainc.openapi.model.AppStatus;
import cn.slainc.openapi.spi.AppService;
import cn.slainc.openapi.spi.AppStore;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

public class DefaultAppService implements AppService {

    private final AppStore store;

    private final ApplicationEventPublisher eventPublisher;

    public DefaultAppService(AppStore store, ApplicationEventPublisher eventPublisher) {
        this.store = store;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public App create(App app) {
        if (app.getAppId() == null || app.getAppId().isBlank()) {
            app = App.create(app.getName(), app.getAppType(), app.getOwnerId());
        }
        if (store.find(app.getAppId()).isPresent()) {
            throw new BizException(ResultCode.DATA_DUPLICATE, "应用 " + app.getAppId() + " 已存在");
        }
        App saved = store.save(app);
        publish(new AppCreatedEvent(saved.getAppId(), saved.getName(), saved.getOwnerId(),
                System.currentTimeMillis()));
        return saved;
    }

    @Override
    public App update(App app) {
        App existing = require(app.getAppId());
        if (!existing.editable()) {
            throw new BizException(ResultCode.DATA_STATE_INVALID,
                    "应用当前状态「" + existing.getStatus().label() + "」不允许编辑，仅草稿与已下线状态可修改");
        }
        existing.setName(app.getName() == null || app.getName().isBlank() ? existing.getName() : app.getName());
        existing.setDescription(app.getDescription());
        existing.setLogo(app.getLogo());
        existing.setCategory(app.getCategory());
        existing.setContact(app.getContact());
        existing.setTags(app.getTags());
        existing.setRedirectUris(app.getRedirectUris());
        existing.setScopes(app.getScopes());
        existing.setUpdatedAt(System.currentTimeMillis());
        return store.save(existing);
    }

    @Override
    public App get(String appId) {
        return require(appId);
    }

    @Override
    public List<App> listByOwner(String ownerId) {
        return store.list().stream().filter(app -> app.getOwnerId().equals(ownerId)).toList();
    }

    @Override
    public List<App> search(String keyword, String category, String tag) {
        return store.list().stream()
                .filter(app -> keyword == null || keyword.isBlank()
                        || app.getName() != null && app.getName().contains(keyword.trim()))
                .filter(app -> category == null || category.isBlank() || category.equals(app.getCategory()))
                .filter(app -> tag == null || tag.isBlank() || app.getTags() != null && app.getTags().contains(tag))
                .toList();
    }

    @Override
    public App transition(String appId, AppAction action) {
        App app = require(appId);
        app.transition(action);
        App saved = store.save(app);
        if (action == AppAction.APPROVE) {
            publish(new AppApprovedEvent(saved.getAppId(), saved.getName(), saved.getOwnerId(),
                    System.currentTimeMillis()));
        }
        return saved;
    }

    @Override
    public List<App> listByStatus(AppStatus status) {
        return store.list().stream().filter(app -> app.getStatus() == status).toList();
    }

    private App require(String appId) {
        Optional<App> found = store.find(appId);
        return found.orElseThrow(() -> new BizException(ResultCode.DATA_NOT_FOUND, "应用 " + appId + " 不存在"));
    }

    private void publish(Object event) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(event);
        }
    }
}
