package cn.slainc.openapi.bridge;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.openapi.SingleProvider;
import cn.slainc.openapi.model.App;
import cn.slainc.openapi.model.AppCredential;
import cn.slainc.openapi.model.AppStatus;
import cn.slainc.openapi.model.AppType;
import cn.slainc.oauth2.client.ClientAdminService;
import cn.slainc.oauth2.client.ClientRecordCache;
import cn.slainc.oauth2.client.ClientRecordRepository;
import cn.slainc.oauth2.client.ClientSecretCipher;
import cn.slainc.oauth2.client.InMemoryClientRecordRepository;
import cn.slainc.oauth2.client.Oauth2ClientRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Oauth2CredentialProvisionerTest {

    private ClientAdminService clientAdminService;

    private Oauth2CredentialProvisioner provisioner;

    @BeforeEach
    void setUp() {
        ClientRecordRepository repository = new InMemoryClientRecordRepository();
        ClientRecordCache cache = new ClientRecordCache(new SingleProvider<>(repository), 30);
        clientAdminService = new ClientAdminService(new SingleProvider<>(repository), cache,
                new ClientSecretCipher("unit-test-key"), new SingleProvider<DynamicConfigService>(null));
        provisioner = new Oauth2CredentialProvisioner(new SingleProvider<>(clientAdminService));
    }

    private App webApp() {
        App app = App.create("网页应用", AppType.WEB, "dev-001");
        app.setStatus(AppStatus.ONLINE);
        app.setScopes(List.of("user_read"));
        app.setRedirectUris(List.of("https://cb.example.com/callback"));
        return app;
    }

    @Test
    void issueRegistersRealOauth2Client() {
        App app = webApp();
        AppCredential credential = provisioner.issue(app);
        assertNotNull(credential.getClientId());
        assertTrue(credential.getClientId().startsWith("cli_"));
        assertNotNull(credential.getClientSecret());
        assertEquals(AppCredential.Type.OAUTH2, credential.getType());

        Oauth2ClientRecord record = clientAdminService.find(credential.getClientId());
        assertEquals("网页应用", record.getClientName());
        assertEquals(List.of("user_read"), record.getContractScopes());
        assertEquals(List.of("https://cb.example.com/callback"), record.getAllowRedirectUris());
        assertTrue(record.getAllowGrantTypes().contains("authorization_code"));
        assertEquals("dev-001", record.getSubjectId());
        assertTrue(record.enabled());
    }

    @Test
    void serverAppNotClaimedByOauth2Bridge() {
        App server = App.create("服务端应用", AppType.SERVER, "dev-001");
        assertTrue(!provisioner.supports(server));
        assertTrue(provisioner.supports(webApp()));
    }

    @Test
    void rotateReturnsPlainSecretWithGracePeriod() {
        App app = webApp();
        AppCredential first = provisioner.issue(app);
        AppCredential rotated = provisioner.rotate(first, app, 3600000L);
        assertEquals(first.getClientId(), rotated.getClientId());
        assertNotEquals(first.getClientSecret(), rotated.getClientSecret());
        assertNotNull(rotated.getPreviousSecretExpireAt());

        Oauth2ClientRecord record = clientAdminService.find(first.getClientId());
        assertTrue(record.getPreviousSecretExpireAt() > System.currentTimeMillis());
    }

    @Test
    void statusChangeSyncedToOauth2Client() {
        App app = webApp();
        AppCredential credential = provisioner.issue(app);
        provisioner.changeStatus(credential.getClientId(), false);
        assertTrue(!clientAdminService.find(credential.getClientId()).enabled());
        provisioner.changeStatus(credential.getClientId(), true);
        assertTrue(clientAdminService.find(credential.getClientId()).enabled());
    }

    @Test
    void secretOnlyAppearsInIssuedResult() {
        App app = webApp();
        AppCredential credential = provisioner.issue(app);
        Oauth2ClientRecord record = clientAdminService.find(credential.getClientId());
        assertNotEquals(credential.getClientSecret(), record.getClientSecret());
        assertTrue(record.getClientSecret().startsWith("ENC("));
        assertNull(record.getPreviousSecret());
    }
}
