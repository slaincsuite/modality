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
        String token = tokenOf(LoginType.USER, "user");
        post("/test/safe/open?type=USER&service=pay&seconds=60", LoginType.USER, token, null);
        assertThat(safeState("USER", "pay", LoginType.USER, token)).isTrue();
    }

    @Test
    void unopenedServiceIsNotSafe() {
        String token = tokenOf(LoginType.USER, "user");
        assertThat(safeState("USER", "transfer", LoginType.USER, token)).isFalse();
    }

    @Test
    void safeStateIsPerService() {
        String token = tokenOf(LoginType.USER, "user");
        post("/test/safe/open?type=USER&service=pay&seconds=60", LoginType.USER, token, null);
        assertThat(safeState("USER", "pay", LoginType.USER, token)).isTrue();
        assertThat(safeState("USER", "transfer", LoginType.USER, token)).isFalse();
    }

    @Test
    void safeExpiresAfterConfiguredTime() throws InterruptedException {
        String token = tokenOf(LoginType.USER, "user");
        post("/test/safe/open?type=USER&service=pay&seconds=1", LoginType.USER, token, null);
        assertThat(safeState("USER", "pay", LoginType.USER, token)).isTrue();
        Thread.sleep(1300);
        assertThat(safeState("USER", "pay", LoginType.USER, token)).isFalse();
    }

    @Test
    void closeSafeRevokesImmediately() {
        String token = tokenOf(LoginType.USER, "user");
        post("/test/safe/open?type=USER&service=pay&seconds=60", LoginType.USER, token, null);
        post("/test/safe/close?type=USER&service=pay", LoginType.USER, token, null);
        assertThat(safeState("USER", "pay", LoginType.USER, token)).isFalse();
    }

    @Test
    void safeIsIsolatedPerLoginType() {
        String userToken = tokenOf(LoginType.USER, "user");
        String adminToken = tokenOf(LoginType.ADMIN, "admin");
        post("/test/safe/open?type=USER&service=pay&seconds=60", LoginType.USER, userToken, null);
        assertThat(safeState("ADMIN", "pay", LoginType.ADMIN, adminToken)).isFalse();
    }

    @Test
    void safeRequiresLoginFirst() {
        var response = get("/test/safe/annotated", LoginType.USER, null);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo(401000);
    }
}
