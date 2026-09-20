package cn.slainc.openapi.autoconfigure;

import cn.slainc.apikey.service.ApiKeyAdminService;
import cn.slainc.oauth2.client.ClientAdminService;
import cn.slainc.openapi.bridge.ApiKeyCredentialProvisioner;
import cn.slainc.openapi.bridge.GeneratedCredentialProvisioner;
import cn.slainc.openapi.bridge.Oauth2CredentialProvisioner;
import cn.slainc.openapi.model.App;
import cn.slainc.openapi.spi.AppService;
import cn.slainc.openapi.spi.AppStore;
import cn.slainc.openapi.spi.ConsentService;
import cn.slainc.openapi.spi.CredentialProvisioner;
import cn.slainc.openapi.spi.CredentialService;
import cn.slainc.openapi.spi.ScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenApiAutoConfiguration.class,
                    Oauth2BridgeAutoConfiguration.class, ApiKeyBridgeAutoConfiguration.class));

    @Test
    void defaultWiringProvidesStoresAndServices() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AppService.class);
            assertThat(context).hasSingleBean(ScopeService.class);
            assertThat(context).hasSingleBean(ConsentService.class);
            assertThat(context).hasSingleBean(CredentialService.class);
            assertThat(context).hasSingleBean(GeneratedCredentialProvisioner.class);
            assertThat(context).hasSingleBean(Oauth2CredentialProvisioner.class);
            assertThat(context).hasSingleBean(ApiKeyCredentialProvisioner.class);
        });
    }

    @Test
    void moduleCanOverrideStoreSpi() {
        ModuleAppStore moduleStore = new ModuleAppStore();
        runner.withBean("moduleAppStore", AppStore.class, () -> moduleStore)
                .run(context -> {
                    assertThat(context).hasSingleBean(AppStore.class);
                    assertThat(context.getBean(AppStore.class)).isSameAs(moduleStore);
                    AppService appService = context.getBean(AppService.class);
                    App app = cn.slainc.openapi.model.App.create("模块应用",
                            cn.slainc.openapi.model.AppType.WEB, "dev-001");
                    appService.create(app);
                    assertThat(moduleStore.saved).isTrue();
                    assertThat(appService.get(app.getAppId()).getName()).isEqualTo("模块应用");
                });
    }

    @Test
    void fallsBackToGeneratedProvisionerWithoutOauth2() {
        runner.run(context -> {
            CredentialService credentialService = context.getBean(CredentialService.class);
            AppService appService = context.getBean(AppService.class);
            App app = cn.slainc.openapi.model.App.create("回退应用",
                    cn.slainc.openapi.model.AppType.WEB, "dev-001");
            appService.create(app);
            var credential = credentialService.issue(app.getAppId());
            assertThat(credential.getType()).isEqualTo(cn.slainc.openapi.model.AppCredential.Type.GENERATED);
        });
    }

    @Test
    void bridgeIssuesRealClientWhenOauth2Available() {
        runner.withBean(ClientAdminService.class, () -> {
                    cn.slainc.oauth2.client.InMemoryClientRecordRepository repository =
                            new cn.slainc.oauth2.client.InMemoryClientRecordRepository();
                    return new cn.slainc.oauth2.client.ClientAdminService(
                            new cn.slainc.openapi.SingleProvider<>(repository),
                            new cn.slainc.oauth2.client.ClientRecordCache(
                                    new cn.slainc.openapi.SingleProvider<>(repository), 30),
                            new cn.slainc.oauth2.client.ClientSecretCipher(null),
                            new cn.slainc.openapi.SingleProvider<>(null));
                })
                .run(context -> {
                    AppService appService = context.getBean(AppService.class);
                    CredentialService credentialService = context.getBean(CredentialService.class);
                    App app = cn.slainc.openapi.model.App.create("桥应用",
                            cn.slainc.openapi.model.AppType.WEB, "dev-001");
                    appService.create(app);
                    var credential = credentialService.issue(app.getAppId());
                    assertThat(credential.getType()).isEqualTo(cn.slainc.openapi.model.AppCredential.Type.OAUTH2);
                    assertThat(context.getBean(ClientAdminService.class)
                            .find(credential.getClientId())).isNotNull();
                });
    }

    @Test
    void masterSwitchDisablesWiring() {
        runner.withPropertyValues("modality.open-api.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(AppService.class));
    }

    static class ModuleAppStore implements AppStore {

        boolean saved;

        private final AppStore delegate = new cn.slainc.openapi.support.MemoryStores.InMemoryAppStore();

        @Override
        public App save(App app) {
            saved = true;
            return delegate.save(app);
        }

        @Override
        public Optional<App> find(String appId) {
            return delegate.find(appId);
        }

        @Override
        public List<App> list() {
            return delegate.list();
        }

        @Override
        public void delete(String appId) {
            delegate.delete(appId);
        }
    }
}
