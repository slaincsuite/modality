package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafeAuthAcceptanceTest extends SecurityAcceptanceTestBase {

    private boolean safeState(String type, String service, LoginType headerType, String token) {
        var response = get("/test/safe/state?type=" + type + "&service=" + service, headerType, token);
        return Boolean.TRUE.equals(JsonPath.parse(response.body()).read("$.data"));
    }

    @Test
    void openSafeMakesServiceSafe() {
        String token = tokenOf(LoginType.TENANT_USER, "user");
        post("/test/safe/open?type=TENANT_USER&service=pay&seconds=60", LoginType.TENANT_USER, token, null);
        assertThat(safeState("TENANT_USER", "pay", LoginType.TENANT_USER, token)).isTrue();
    }

    @Test
    void unopenedServiceIsNotSafe() {
        String token = tokenOf(LoginType.TENANT_USER, "user");
        assertThat(safeState("TENANT_USER", "transfer", LoginType.TENANT_USER, token)).isFalse();
    }

    @Test
    void safeStateIsPerService() {
        String token = tokenOf(LoginType.TENANT_USER, "user");
        post("/test/safe/open?type=TENANT_USER&service=pay&seconds=60", LoginType.TENANT_USER, token, null);
        assertThat(safeState("TENANT_USER", "pay", LoginType.TENANT_USER, token)).isTrue();
        assertThat(safeState("TENANT_USER", "transfer", LoginType.TENANT_USER, token)).isFalse();
    }

    @Test
    void safeExpiresAfterConfiguredTime() throws InterruptedException {
        String token = tokenOf(LoginType.TENANT_USER, "user");
        post("/test/safe/open?type=TENANT_USER&service=pay&seconds=1", LoginType.TENANT_USER, token, null);
        assertThat(safeState("TENANT_USER", "pay", LoginType.TENANT_USER, token)).isTrue();
        Thread.sleep(1300);
        assertThat(safeState("TENANT_USER", "pay", LoginType.TENANT_USER, token)).isFalse();
    }

    @Test
    void closeSafeRevokesImmediately() {
        String token = tokenOf(LoginType.TENANT_USER, "user");
        post("/test/safe/open?type=TENANT_USER&service=pay&seconds=60", LoginType.TENANT_USER, token, null);
        post("/test/safe/close?type=TENANT_USER&service=pay", LoginType.TENANT_USER, token, null);
        assertThat(safeState("TENANT_USER", "pay", LoginType.TENANT_USER, token)).isFalse();
    }

    @Test
    void safeIsIsolatedPerLoginType() {
        String userToken = tokenOf(LoginType.TENANT_USER, "user");
        String adminToken = tokenOf(LoginType.PLATFORM_ADMIN, "admin");
        post("/test/safe/open?type=TENANT_USER&service=pay&seconds=60", LoginType.TENANT_USER, userToken, null);
        assertThat(safeState("PLATFORM_ADMIN", "pay", LoginType.PLATFORM_ADMIN, adminToken)).isFalse();
    }

    @Test
    void safeRequiresLoginFirst() {
        var response = get("/test/safe/annotated", LoginType.TENANT_USER, null);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo(401000);
    }
}
