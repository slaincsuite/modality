package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAnnotationHttpAcceptanceTest extends SecurityAcceptanceTestBase {

    @Test
    void requireLoginWithoutTokenRejected() {
        var response = get("/test/annotated/role-all", LoginType.USER, null);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo(401000);
        assertThat(messageOf(response)).isEqualTo("未能读取到有效 token");
    }

    @Test
    void annotationCheckMapsNotLoginToUnified401Result() {
        config.set("modality.security.filter.exclude-patterns",
                "[\"/test/annotated/safe-only\",\"/auth/login\",\"/auth/logout\"]");
        var response = get("/test/annotated/safe-only", LoginType.USER, null);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(codeOf(response)).as(response.body()).isEqualTo(401000);
        assertThat(messageOf(response)).isEqualTo("未能读取到有效 token");
    }

    @Test
    void requireLoginWithValidTokenPasses() {
        String token = tokenOf(LoginType.USER, "user");
        var response = get("/test/user/me", LoginType.USER, token);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(Long.parseLong(String.valueOf((Object) JsonPath.parse(response.body()).read("$.data"))))
                .isEqualTo(2001L);
    }

    @Test
    void requireRoleAllPassesWhenBothRolesGranted() {
        String token = tokenOf(LoginType.USER, "user");
        var response = get("/test/annotated/role-all", LoginType.USER, token);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(dataOf(response)).contains("同时拥有");
    }

    @Test
    void requireRoleAllFailsWithRoleNameWhenRoleMissing() {
        accounts.register("limited-user", 2999L, LoginType.USER, true, false, null);
        var login = post("/auth/login", LoginType.USER, null, """
                {"username":"limited-user","password":"Password#123","deviceType":"","deviceId":""}
                """);
        String token = JsonPath.parse(login.body()).read("$.data.tokenValue");
        var response = get("/test/annotated/role-all", LoginType.USER, token);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(codeOf(response)).isEqualTo(403000);
        assertThat(messageOf(response)).contains("无此角色");
    }

    @Test
    void requireRoleAnyPassesWithOperatorRole() {
        String token = tokenOf(LoginType.USER, "user");
        var response = get("/test/annotated/role-any", LoginType.USER, token);
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void requirePermissionFailsWithPermissionNameWhenMissing() {
        accounts.register("limited-user", 2999L, LoginType.USER, true, false, null);
        var login = post("/auth/login", LoginType.USER, null, """
                {"username":"limited-user","password":"Password#123","deviceType":"","deviceId":""}
                """);
        String token = JsonPath.parse(login.body()).read("$.data.tokenValue");
        var response = get("/test/annotated/perm-all", LoginType.USER, token);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(codeOf(response)).isEqualTo(403000);
        assertThat(messageOf(response)).contains("无此权限");
    }

    @Test
    void requirePermissionAnyFailsWhenNoneGranted() {
        accounts.register("limited-user", 2999L, LoginType.USER, true, false, null);
        var login = post("/auth/login", LoginType.USER, null, """
                {"username":"limited-user","password":"Password#123","deviceType":"","deviceId":""}
                """);
        String token = JsonPath.parse(login.body()).read("$.data.tokenValue");
        var response = get("/test/annotated/perm-any", LoginType.USER, token);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(codeOf(response)).as(response.body()).isEqualTo(403000);
    }

    @Test
    void requirePermissionAllPassesForUser() {
        String token = tokenOf(LoginType.USER, "user");
        var response = get("/test/annotated/perm-all", LoginType.USER, token);
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void adminTokenDoesNotSatisfyUserSystemAnnotation() {
        String token = tokenOf(LoginType.ADMIN, "admin");
        var response = get("/test/annotated/perm-all", LoginType.ADMIN, token);
        // 该注解默认校验 USER 体系，而 ADMIN 的会话在 USER 体系里并不存在
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo(401000);
    }

    @Test
    void requirePermissionAnyPassesWithExportPermission() {
        String token = tokenOf(LoginType.USER, "user");
        var response = get("/test/annotated/perm-any", LoginType.USER, token);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(dataOf(response)).contains("任一权限");
    }

    @Test
    void requireSafeRejectsWhenNotOpened() {
        String token = tokenOf(LoginType.USER, "user");
        var response = get("/test/safe/annotated", LoginType.USER, token);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(codeOf(response)).isEqualTo(403000);
        assertThat(messageOf(response)).contains("二级认证");
    }

    @Test
    void requireSafePassesAfterOpenSafe() {
        String token = tokenOf(LoginType.USER, "user");
        post("/test/safe/open?type=USER&service=DEFAULT&seconds=60", LoginType.USER, token, null);
        var response = get("/test/safe/annotated", LoginType.USER, token);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(dataOf(response)).contains("二次认证通过");
    }

    @Test
    void builtInSaCheckLoginPassesForAdmin() {
        String token = tokenOf(LoginType.ADMIN, "admin");
        var response = get("/test/annotated/sa-check-login", LoginType.ADMIN, token);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(dataOf(response)).contains("内置注解通过");
    }

    @Test
    void builtInSaCheckLoginRejectsUserToken() {
        String token = tokenOf(LoginType.USER, "user");
        var response = get("/test/annotated/sa-check-login", LoginType.USER, token);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo(401000);
    }
}
