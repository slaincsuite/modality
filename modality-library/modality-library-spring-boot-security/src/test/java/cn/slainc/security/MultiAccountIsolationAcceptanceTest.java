package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.login.LoginResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MultiAccountIsolationAcceptanceTest extends SecurityAcceptanceTestBase {

    @Test
    void threeSystemsCanLoginIndependently() {
        LoginResult admin = login(LoginType.PLATFORM_ADMIN, "admin");
        LoginResult user = login(LoginType.TENANT_USER, "user");
        LoginResult client = login(LoginType.APP, "client");
        assertThat(admin.loginType()).isEqualTo("PLATFORM_ADMIN");
        assertThat(user.loginType()).isEqualTo("TENANT_USER");
        assertThat(client.loginType()).isEqualTo("APP");
    }

    @Test
    void sixLoginableSystemsEachCarryOwnIdentity() {
        LoginResult platformAdmin = login(LoginType.PLATFORM_ADMIN, "admin");
        LoginResult platformUser = login(LoginType.PLATFORM_USER, "platform-user");
        LoginResult tenantAdmin = login(LoginType.TENANT_ADMIN, "tenant-admin");
        LoginResult tenantUser = login(LoginType.TENANT_USER, "user");
        LoginResult app = login(LoginType.APP, "client");
        LoginResult service = login(LoginType.SERVICE, "service");
        assertThat(platformAdmin.loginType()).isEqualTo("PLATFORM_ADMIN");
        assertThat(platformAdmin.loginId()).isEqualTo(1001L);
        assertThat(platformUser.loginType()).isEqualTo("PLATFORM_USER");
        assertThat(platformUser.loginId()).isEqualTo(1101L);
        assertThat(tenantAdmin.loginType()).isEqualTo("TENANT_ADMIN");
        assertThat(tenantAdmin.loginId()).isEqualTo(2101L);
        assertThat(tenantUser.loginType()).isEqualTo("TENANT_USER");
        assertThat(app.loginType()).isEqualTo("APP");
        assertThat(service.loginType()).isEqualTo("SERVICE");
        assertThat(service.loginId()).isEqualTo(3101L);
    }

    @Test
    void tokensFromOtherFiveSystemsCannotAccessTenantUserEndpoint() {
        assertThat(get("/test/user/me", LoginType.TENANT_USER,
                tokenOf(LoginType.TENANT_USER, "user")).statusCode()).isEqualTo(200);
        assertThat(get("/test/user/me", LoginType.TENANT_USER,
                tokenOf(LoginType.PLATFORM_ADMIN, "admin")).statusCode()).isEqualTo(401);
        assertThat(get("/test/user/me", LoginType.TENANT_USER,
                tokenOf(LoginType.PLATFORM_USER, "platform-user")).statusCode()).isEqualTo(401);
        assertThat(get("/test/user/me", LoginType.TENANT_USER,
                tokenOf(LoginType.TENANT_ADMIN, "tenant-admin")).statusCode()).isEqualTo(401);
        assertThat(get("/test/user/me", LoginType.TENANT_USER,
                tokenOf(LoginType.APP, "client")).statusCode()).isEqualTo(401);
        assertThat(get("/test/user/me", LoginType.TENANT_USER,
                tokenOf(LoginType.SERVICE, "service")).statusCode()).isEqualTo(401);
    }

    @Test
    void guestSystemRegisteredButNeverIssuesAccount() {
        assertThat(StpKit.guest().getLoginType()).isEqualTo("GUEST");
        assertThat(accounts.findAccount("anyone", LoginType.GUEST)).isNull();
        var response = post("/auth/login", LoginType.GUEST, null, """
                {"username":"anyone","password":"Password#123","deviceType":"","deviceId":""}
                """);
        assertThat(response.body()).contains("账号不存在或密码错误");
        assertThat(response.body()).doesNotContain("tokenValue");
    }

    @Test
    void userTokenCannotAccessAdminEndpoint() {
        String userToken = tokenOf(LoginType.TENANT_USER, "user");
        var response = get("/test/admin/panel", LoginType.PLATFORM_ADMIN, userToken);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo(401000);
    }

    @Test
    void adminTokenCannotAccessUserEndpoint() {
        String adminToken = tokenOf(LoginType.PLATFORM_ADMIN, "admin");
        var response = get("/test/user/me", LoginType.TENANT_USER, adminToken);
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void clientTokenCannotAccessUserEndpoint() {
        String clientToken = tokenOf(LoginType.APP, "client");
        var response = get("/test/user/me", LoginType.TENANT_USER, clientToken);
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void eachSystemAccessesItsOwnEndpoint() {
        assertThat(get("/test/admin/panel", LoginType.PLATFORM_ADMIN, tokenOf(LoginType.PLATFORM_ADMIN, "admin")).statusCode())
                .isEqualTo(200);
        assertThat(get("/test/user/me", LoginType.TENANT_USER, tokenOf(LoginType.TENANT_USER, "user")).statusCode())
                .isEqualTo(200);
        assertThat(get("/test/client/info", LoginType.APP, tokenOf(LoginType.APP, "client")).statusCode())
                .isEqualTo(200);
    }

    @Test
    void sameLoginIdAcrossSystemsIsolatesSessions() {
        login(LoginType.TENANT_USER, "user");
        String userToken = tokenOf(LoginType.TENANT_USER, "user");
        String adminToken = tokenOf(LoginType.PLATFORM_ADMIN, "admin");
        var response = post("/test/admin/kick?type=TENANT_USER&loginId=2001", LoginType.PLATFORM_ADMIN,
                tokenOf(LoginType.PLATFORM_ADMIN, "admin"), null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(get("/test/user/me", LoginType.TENANT_USER, userToken).statusCode()).isEqualTo(401);
        assertThat(get("/test/admin/panel", LoginType.PLATFORM_ADMIN, adminToken).statusCode()).isEqualTo(200);
    }

    @Test
    void kickInUserSystemDoesNotAffectClientSystemSameId() {
        String clientToken = tokenOf(LoginType.APP, "client");
        post("/test/admin/kick?type=TENANT_USER&loginId=3001", LoginType.PLATFORM_ADMIN, tokenOf(LoginType.PLATFORM_ADMIN, "admin"), null);
        assertThat(get("/test/client/info", LoginType.APP, clientToken).statusCode()).isEqualTo(200);
    }

    @Test
    void permissionsDifferAcrossSystemsForSameUserId() {
        var adminPerms = get("/test/permissions?type=PLATFORM_ADMIN&loginId=1001", null, null);
        var userPerms = get("/test/permissions?type=TENANT_USER&loginId=1001", null, null);
        var clientPerms = get("/test/permissions?type=APP&loginId=1001", null, null);
        assertThat(dataListOf(adminPerms)).contains("system:user:list");
        assertThat(dataListOf(userPerms)).contains("user:info");
        assertThat(dataListOf(clientPerms)).contains("client:sync");
        assertThat(dataListOf(adminPerms)).doesNotContain("client:sync", "user:info");
    }

    @Test
    void sessionsAreIndependentAcrossSystemsForSameId() {
        String adminToken = tokenOf(LoginType.PLATFORM_ADMIN, "admin");
        String userToken = tokenOf(LoginType.TENANT_USER, "user");
        assertThat(adminToken).isNotEqualTo(userToken);
        assertThat(get("/test/admin/panel", LoginType.PLATFORM_ADMIN, adminToken).statusCode()).isEqualTo(200);
        assertThat(get("/test/user/me", LoginType.TENANT_USER, userToken).statusCode()).isEqualTo(200);
    }

    @Test
    void logoutOneSystemKeepsOtherSystemAlive() {
        String userToken = tokenOf(LoginType.TENANT_USER, "user");
        String clientToken = tokenOf(LoginType.APP, "client");
        post("/auth/logout", LoginType.TENANT_USER, userToken, null);
        assertThat(get("/test/user/me", LoginType.TENANT_USER, userToken).statusCode()).isEqualTo(401);
        assertThat(get("/test/client/info", LoginType.APP, clientToken).statusCode()).isEqualTo(200);
    }

    @Test
    void stpKitReturnsDistinctLogicPerSystem() {
        assertThat(StpKit.platformAdmin().getLoginType()).isEqualTo("PLATFORM_ADMIN");
        assertThat(StpKit.tenantUser().getLoginType()).isEqualTo("TENANT_USER");
        assertThat(StpKit.app().getLoginType()).isEqualTo("APP");
        assertThat(StpKit.of(LoginType.PLATFORM_ADMIN)).isSameAs(StpKit.platformAdmin());
    }
}
