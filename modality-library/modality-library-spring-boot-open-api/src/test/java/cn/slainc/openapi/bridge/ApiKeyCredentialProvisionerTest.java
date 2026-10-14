package cn.slainc.openapi.bridge;

import cn.dev33.satoken.apikey.model.ApiKeyModel;
import cn.slainc.apikey.admin.ApiKeyAdminService;
import cn.slainc.openapi.SingleProvider;
import cn.slainc.openapi.domain.App;
import cn.slainc.openapi.domain.AppCredential;
import cn.slainc.openapi.domain.AppType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyCredentialProvisionerTest {

    @Mock
    private ApiKeyAdminService adminService;

    private ApiKeyCredentialProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = new ApiKeyCredentialProvisioner(new SingleProvider<>(adminService));
    }

    private App serverApp() {
        App app = App.create("服务端应用", AppType.SERVER, "dev-001");
        app.setScopes(List.of("order_read"));
        return app;
    }

    @Test
    void onlyClaimsServerApps() {
        assertTrue(provisioner.supports(serverApp()));
        App web = App.create("网页应用", AppType.WEB, "dev-001");
        assertFalse(provisioner.supports(web));
    }

    @Test
    void issueDelegatesToApiKeyAdminServiceWithAppInfo() {
        ApiKeyModel model = new ApiKeyModel();
        model.setApiKey("sk_demo1234567890");
        when(adminService.issue(anyString(), anyString(), any(), any(), any())).thenReturn(model);

        AppCredential credential = provisioner.issue(serverApp());
        assertEquals("sk_demo1234567890", credential.getClientId());
        assertEquals(AppCredential.Type.API_KEY, credential.getType());

        ArgumentCaptor<Map<String, Object>> extra = ArgumentCaptor.forClass(Map.class);
        verify(adminService).issue(eq("dev-001"), eq("服务端应用"), eq(List.of("order_read")), isNull(), extra.capture());
        assertTrue(extra.getValue().containsKey("appId"));
    }

    @Test
    void rotateConvertsHoursAndPassesGrace() {
        ApiKeyModel fresh = new ApiKeyModel();
        fresh.setApiKey("sk_new9876543210");
        when(adminService.rotate(anyString(), anyLong())).thenReturn(fresh);

        AppCredential rotated = provisioner.rotate(existing(), serverApp(), 7200000L);
        assertEquals("sk_new9876543210", rotated.getClientId());
        verify(adminService).rotate("sk_old0000000000", 2L);
    }

    @Test
    void enableDisableDispatchToCorrespondingMethod() {
        provisioner.changeStatus("sk_x", true);
        verify(adminService).enable("sk_x");
        verify(adminService, never()).disable(anyString());
        provisioner.changeStatus("sk_x", false);
        verify(adminService).disable("sk_x");
    }

    private AppCredential existing() {
        AppCredential credential = new AppCredential();
        credential.setClientId("sk_old0000000000");
        credential.setType(AppCredential.Type.API_KEY);
        return credential;
    }
}
