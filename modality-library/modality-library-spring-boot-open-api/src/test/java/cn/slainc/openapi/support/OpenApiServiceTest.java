package cn.slainc.openapi.support;

import cn.slainc.core.exception.BizException;
import cn.slainc.openapi.bridge.GeneratedCredentialProvisioner;
import cn.slainc.openapi.event.AppApprovedEvent;
import cn.slainc.openapi.event.AppCreatedEvent;
import cn.slainc.openapi.event.ConsentGrantedEvent;
import cn.slainc.openapi.event.ConsentRevokedEvent;
import cn.slainc.openapi.model.App;
import cn.slainc.openapi.model.AppStatus;
import cn.slainc.openapi.model.AppType;
import cn.slainc.openapi.model.ConsentRecord;
import cn.slainc.openapi.model.OpenScope;
import cn.slainc.openapi.model.ScopeRiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiServiceTest {

    private final List<Object> events = new ArrayList<>();

    private MemoryStores.InMemoryAppStore appStore;

    private MemoryStores.InMemoryScopeStore scopeStore;

    private MemoryStores.InMemoryConsentStore consentStore;

    private DefaultAppService appService;

    private DefaultScopeService scopeService;

    private DefaultConsentService consentService;

    @BeforeEach
    void setUp() {
        events.clear();
        appStore = new MemoryStores.InMemoryAppStore();
        scopeStore = new MemoryStores.InMemoryScopeStore();
        consentStore = new MemoryStores.InMemoryConsentStore();
        appService = new DefaultAppService(appStore, events::add);
        scopeService = new DefaultScopeService(scopeStore);
        consentService = new DefaultConsentService(consentStore, appStore, scopeStore, events::add);
    }

    private App createOnlineApp(String name, AppType type) {
        App app = App.create(name, type, "dev-001");
        app.setScopes(List.of("user_read"));
        appService.create(app);
        appService.submit(app.getAppId());
        return appService.approve(app.getAppId());
    }

    @Test
    void createAndPublishPublishesEvents() {
        App app = App.create("应用甲", AppType.WEB, "dev-001");
        App created = appService.create(app);
        assertEquals(AppStatus.DRAFT, created.getStatus());
        appService.submit(created.getAppId());
        App approved = appService.approve(created.getAppId());
        assertEquals(AppStatus.ONLINE, approved.getStatus());
        assertTrue(events.stream().anyMatch(event -> event instanceof AppCreatedEvent createdEvent
                && createdEvent.appId().equals(app.getAppId())));
        assertTrue(events.stream().anyMatch(event -> event instanceof AppApprovedEvent approvedEvent
                && approvedEvent.appId().equals(app.getAppId())));
    }

    @Test
    void onlineStatusNotEditable() {
        App app = createOnlineApp("应用乙", AppType.WEB);
        App modified = new App();
        modified.setAppId(app.getAppId());
        modified.setName("新名字");
        assertThrows(BizException.class, () -> appService.update(modified));
        appService.offline(app.getAppId());
        appService.update(modified);
        assertEquals("新名字", appStore.find(app.getAppId()).orElseThrow().getName());
    }

    @Test
    void searchByNameCategoryTag() {
        App first = App.create("订单中心", AppType.WEB, "dev-001");
        first.setCategory("电商");
        first.setTags(List.of("订单", "交易"));
        appService.create(first);
        App second = App.create("用户中心", AppType.WEB, "dev-002");
        second.setCategory("基础");
        second.setTags(List.of("用户"));
        appService.create(second);
        assertEquals(1, appService.search("订单", null, null).size());
        assertEquals(1, appService.search(null, "电商", null).size());
        assertEquals(1, appService.search(null, null, "用户").size());
        assertEquals(2, appService.search(null, null, null).size());
        assertEquals(1, appService.listByOwner("dev-002").size());
    }

    @Test
    void highRiskScopeForcesAudit() {
        OpenScope high = new OpenScope();
        high.setName("pay_transfer");
        high.setTitle("转账");
        high.setRiskLevel(ScopeRiskLevel.HIGH);
        scopeService.register(high);
        assertTrue(scopeService.requiresAudit("pay_transfer"));

        OpenScope low = new OpenScope();
        low.setName("user_read");
        low.setTitle("读取用户");
        low.setRiskLevel(ScopeRiskLevel.LOW);
        scopeService.register(low);
        assertFalse(scopeService.requiresAudit("user_read"));

        assertEquals(2, scopeService.listByGroup("默认").size());
        assertThrows(BizException.class, () -> scopeService.register(low));
    }

    @Test
    void scopeNamingConstraint() {
        OpenScope invalid = new OpenScope();
        invalid.setName("User-Read");
        invalid.setTitle("非法命名");
        BizException exception = assertThrows(BizException.class, () -> scopeService.register(invalid));
        assertTrue(exception.getMessage().contains("小写字母"));
    }

    @Test
    void grantMergesScopesAndPublishesEvent() {
        scopeService.register(scope("user_read"));
        scopeService.register(scope("user_write"));
        App app = createOnlineApp("应用丙", AppType.WEB);

        ConsentRecord granted = consentService.grant("1001", app.getAppId(), List.of("user_read"), null);
        assertTrue(granted.active());
        assertEquals(List.of("user_read"), granted.getScopes());

        ConsentRecord merged = consentService.grant("1001", app.getAppId(), List.of("user_write"), null);
        assertEquals(List.of("user_read", "user_write"), merged.getScopes());
        assertEquals(1, consentService.listByUser("1001").size());
        assertTrue(consentService.isGranted("1001", app.getAppId(), "user_write"));

        assertTrue(events.stream().filter(event -> event instanceof ConsentGrantedEvent).count() >= 2);
    }

    @Test
    void revokeAndSingleScopeRevoke() {
        scopeService.register(scope("user_read"));
        scopeService.register(scope("user_write"));
        App app = createOnlineApp("应用丁", AppType.WEB);
        consentService.grant("1002", app.getAppId(), List.of("user_read", "user_write"), null);

        ConsentRecord narrowed = consentService.revokeScope("1002", app.getAppId(), "user_write");
        assertTrue(narrowed.active());
        assertEquals(List.of("user_read"), narrowed.getScopes());

        ConsentRecord revoked = consentService.revoke("1002", app.getAppId());
        assertFalse(revoked.active());
        assertFalse(consentService.isGranted("1002", app.getAppId(), "user_read"));
        assertThrows(BizException.class, () -> consentService.revoke("1002", app.getAppId()));
        assertTrue(events.stream().anyMatch(event -> event instanceof ConsentRevokedEvent));
    }

    @Test
    void revokingAllScopesRevokesWholeConsent() {
        scopeService.register(scope("user_read"));
        App app = createOnlineApp("应用戊", AppType.WEB);
        consentService.grant("1003", app.getAppId(), List.of("user_read"), null);
        ConsentRecord revoked = consentService.revokeScope("1003", app.getAppId(), "user_read");
        assertFalse(revoked.active());
    }

    @Test
    void offlineAppAndUnregisteredScopeRejected() {
        scopeService.register(scope("user_read"));
        App draft = App.create("草稿应用", AppType.WEB, "dev-001");
        draft.setScopes(List.of("user_read"));
        appService.create(draft);
        assertThrows(BizException.class,
                () -> consentService.grant("1004", draft.getAppId(), List.of("user_read"), null));

        App online = createOnlineApp("应用己", AppType.WEB);
        BizException exception = assertThrows(BizException.class,
                () -> consentService.grant("1004", online.getAppId(), List.of("not_registered"), null));
        assertTrue(exception.getMessage().contains("未在平台登记"));
    }

    @Test
    void credentialIssueRotateStatusViaGeneratedProvisioner() {
        DefaultCredentialService credentialService = new DefaultCredentialService(appStore,
                new MemoryStores.InMemoryCredentialStore(),
                List.of(new GeneratedCredentialProvisioner()));
        App app = createOnlineApp("应用庚", AppType.WEB);
        assertNotNull(credentialService.issue(app.getAppId()));
        assertThrows(BizException.class, () -> credentialService.issue(app.getAppId()));
        credentialService.rotate(app.getAppId(), 60000L);
        credentialService.disable(app.getAppId());
        assertEquals(cn.slainc.openapi.model.AppCredential.Status.DISABLED,
                credentialService.find(app.getAppId()).getStatus());
        credentialService.enable(app.getAppId());
        assertEquals(cn.slainc.openapi.model.AppCredential.Status.ENABLED,
                credentialService.find(app.getAppId()).getStatus());
    }

    private OpenScope scope(String name) {
        OpenScope scope = new OpenScope();
        scope.setName(name);
        scope.setTitle(name);
        return scope;
    }
}
