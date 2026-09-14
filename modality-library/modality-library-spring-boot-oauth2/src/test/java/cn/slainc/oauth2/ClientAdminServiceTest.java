package cn.slainc.oauth2;

import cn.slainc.oauth2.client.ClientAdminService;
import cn.slainc.oauth2.client.Oauth2ClientRecord;
import cn.dev33.satoken.oauth2.data.model.loader.SaClientModel;
import cn.dev33.satoken.oauth2.SaOAuth2Manager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = Oauth2TestApplication.class)
class ClientAdminServiceTest {

    @Autowired
    private ClientAdminService clientAdminService;

    @BeforeEach
    void cleanUp() {
        clientAdminService.list().forEach(record -> clientAdminService.remove(record.getClientId()));
    }

    @Test
    void registerValidatesInputAndDuplicates() {
        Oauth2ClientRecord record = validRecord("dup-app");
        clientAdminService.register(record);

        assertThatThrownBy(() -> clientAdminService.register(validRecord("dup-app")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已存在");
        assertThatThrownBy(() -> clientAdminService.register(validRecord("bad id!")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client_id");
        Oauth2ClientRecord badGrant = validRecord("bad-grant");
        badGrant.setAllowGrantTypes(List.of("magic"));
        assertThatThrownBy(() -> clientAdminService.register(badGrant))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("授权类型");
        Oauth2ClientRecord badRedirect = validRecord("bad-redirect");
        badRedirect.setAllowRedirectUris(List.of("ftp://x/cb"));
        assertThatThrownBy(() -> clientAdminService.register(badRedirect))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("redirect_uri");
    }

    @Test
    void newlyRegisteredClientIsImmediatelyVisibleToDataLoader() {
        clientAdminService.register(validRecord("visible-app"));

        SaClientModel model = SaOAuth2Manager.getDataLoader().getClientModel("visible-app");
        assertThat(model).isNotNull();
        assertThat(model.getClientSecret()).isEqualTo("visible_app-secret");
        assertThat(model.getAllowGrantTypes()).containsExactly("password");
    }

    @Test
    void rotateSecretKeepsPreviousSecretDuringGraceWindow() {
        clientAdminService.register(validRecord("rotate-app"));
        Oauth2ClientRecord rotated = clientAdminService.rotateSecret("rotate-app", 60_000);

        SaClientModel model = SaOAuth2Manager.getDataLoader().getClientModel("rotate-app");
        assertThat(model.getClientSecret()).isEqualTo(rotated.getClientSecret());
        assertThat(SaOAuth2Manager.getTemplate().checkClientSecret("rotate-app", rotated.getClientSecret())
                .getClientId()).isEqualTo("rotate-app");
        assertThat(SaOAuth2Manager.getTemplate().checkClientSecret("rotate-app", "rotate_app-secret")
                .getClientId()).isEqualTo("rotate-app");
        assertThatThrownBy(() -> SaOAuth2Manager.getTemplate().checkClientSecret("rotate-app", "wrong"))
                .hasMessageContaining("client_secret");
    }

    @Test
    void expiredPreviousSecretIsRejected() {
        clientAdminService.register(validRecord("expired-app"));
        clientAdminService.rotateSecret("expired-app", -1);

        assertThatThrownBy(() -> SaOAuth2Manager.getTemplate().checkClientSecret("expired-app", "expired_app-secret"))
                .hasMessageContaining("client_secret");
    }

    private Oauth2ClientRecord validRecord(String clientId) {
        Oauth2ClientRecord record = new Oauth2ClientRecord();
        record.setClientId(clientId);
        record.setClientSecret(clientId.replace('-', '_') + "-secret");
        record.setContractScopes(List.of("openid"));
        record.setAllowRedirectUris(List.of("http://cb.example.com/cb"));
        record.setAllowGrantTypes(List.of("password"));
        return record;
    }
}
