package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.login.LoginResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MultiAccountIsolationAcceptanceTest extends SecurityAcceptanceTestBase {

    @Test
    void threeSystemsCanLoginIndependently() {
        LoginResult admin = login(LoginType.ADMIN, "admin");
        LoginResult user = login(LoginType.USER, "user");
        LoginResult client = login(LoginType.CLIENT, "client");
        assertThat(admin.loginType()).isEqualTo("ADMIN");
        assertThat(user.loginType()).isEqualTo("USER");
        assertThat(client.loginType()).isEqualTo("CLIENT");
    }

    @Test
    void userTokenCannotAccessAdminEndpoint() {
        String userToken = tokenOf(LoginType.USER, "user");
        var response = get("/test/admin/panel", LoginType.ADMIN, userToken);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo(401000);
    }

    @Test
    void adminTokenCannotAccessUserEndpoint() {
        String adminToken = tokenOf(LoginType.ADMIN, "admin");
        var response = get("/test/user/me", LoginType.USER, adminToken);
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void clientTokenCannotAccessUserEndpoint() {
        String clientToken = tokenOf(LoginType.CLIENT, "client");
        var response = get("/test/user/me", LoginType.USER, clientToken);
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void eachSystemAccessesItsOwnEndpoint() {
        assertThat(get("/test/admin/panel", LoginType.ADMIN, tokenOf(LoginType.ADMIN, "admin")).statusCode())
                .isEqualTo(200);
        assertThat(get("/test/user/me", LoginType.USER, tokenOf(LoginType.USER, "user")).statusCode())
                .isEqualTo(200);
        assertThat(get("/test/client/info", LoginType.CLIENT, tokenOf(LoginType.CLIENT, "client")).statusCode())
                .isEqualTo(200);
    }

    @Test
    void sameLoginIdAcrossSystemsIsolatesSessions() {
        login(LoginType.USER, "user");
        String userToken = tokenOf(LoginType.USER, "user");
        String adminToken = tokenOf(LoginType.ADMIN, "admin");
        var response = post("/test/admin/kick?type=USER&loginId=2001", LoginType.ADMIN,
                tokenOf(LoginType.ADMIN, "admin"), null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(get("/test/user/me", LoginType.USER, userToken).statusCode()).isEqualTo(401);
        assertThat(get("/test/admin/panel", LoginType.ADMIN, adminToken).statusCode()).isEqualTo(200);
    }

    @Test
    void kickInUserSystemDoesNotAffectClientSystemSameId() {
        String clientToken = tokenOf(LoginType.CLIENT, "client");
        post("/test/admin/kick?type=USER&loginId=3001", LoginType.ADMIN, tokenOf(LoginType.ADMIN, "admin"), null);
        assertThat(get("/test/client/info", LoginType.CLIENT, clientToken).statusCode()).isEqualTo(200);
    }

    @Test
    void permissionsDifferAcrossSystemsForSameUserId() {
        var adminPerms = get("/test/permissions?type=ADMIN&loginId=1001", null, null);
        var userPerms = get("/test/permissions?type=USER&loginId=1001", null, null);
        var clientPerms = get("/test/permissions?type=CLIENT&loginId=1001", null, null);
        assertThat(dataListOf(adminPerms)).contains("system:user:list");
        assertThat(dataListOf(userPerms)).contains("user:info");
        assertThat(dataListOf(clientPerms)).contains("client:sync");
        assertThat(dataListOf(adminPerms)).doesNotContain("client:sync", "user:info");
    }

    @Test
    void sessionsAreIndependentAcrossSystemsForSameId() {
        String adminToken = tokenOf(LoginType.ADMIN, "admin");
        String userToken = tokenOf(LoginType.USER, "user");
        assertThat(adminToken).isNotEqualTo(userToken);
        assertThat(get("/test/admin/panel", LoginType.ADMIN, adminToken).statusCode()).isEqualTo(200);
        assertThat(get("/test/user/me", LoginType.USER, userToken).statusCode()).isEqualTo(200);
    }

    @Test
    void logoutOneSystemKeepsOtherSystemAlive() {
        String userToken = tokenOf(LoginType.USER, "user");
        String clientToken = tokenOf(LoginType.CLIENT, "client");
        post("/auth/logout", LoginType.USER, userToken, null);
        assertThat(get("/test/user/me", LoginType.USER, userToken).statusCode()).isEqualTo(401);
        assertThat(get("/test/client/info", LoginType.CLIENT, clientToken).statusCode()).isEqualTo(200);
    }

    @Test
    void stpKitReturnsDistinctLogicPerSystem() {
        assertThat(StpKit.admin().getLoginType()).isEqualTo("ADMIN");
        assertThat(StpKit.user().getLoginType()).isEqualTo("USER");
        assertThat(StpKit.client().getLoginType()).isEqualTo("CLIENT");
        assertThat(StpKit.of(LoginType.ADMIN)).isSameAs(StpKit.admin());
    }
}
