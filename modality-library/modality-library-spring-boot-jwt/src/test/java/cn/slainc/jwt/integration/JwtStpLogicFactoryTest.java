package cn.slainc.jwt.integration;

import cn.dev33.satoken.jwt.StpLogicJwtForMixin;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.jwt.StpLogicJwtForStateless;
import cn.dev33.satoken.stp.StpLogic;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtStpLogicFactoryTest {

    private static final String ORIGINAL_SECRET = cn.dev33.satoken.SaManager.getConfig().getJwtSecretKey();

    @BeforeAll
    static void beforeAll() {
        cn.dev33.satoken.SaManager.getConfig().setJwtSecretKey("jwt-test-secret-key-0123456789abcdef");
    }

    @AfterAll
    static void afterAll() {
        cn.dev33.satoken.SaManager.getConfig().setJwtSecretKey(ORIGINAL_SECRET);
    }

    private final JwtStpLogicFactory factory = new JwtStpLogicFactory();

    @Test
    void simpleStpLogicCreatedByDefault() {
        StpLogic stpLogic = factory.create("PLATFORM_ADMIN");
        assertThat(stpLogic).isInstanceOf(StpLogicJwtForSimple.class);
        assertThat(stpLogic.getLoginType()).isEqualTo("PLATFORM_ADMIN");
    }

    @Test
    void threeModesCreateCorrespondingImplementations() {
        assertThat(factory.create("TENANT_USER", JwtStpMode.SIMPLE)).isInstanceOf(StpLogicJwtForSimple.class);
        assertThat(factory.create("TENANT_USER", JwtStpMode.MIXIN)).isInstanceOf(StpLogicJwtForMixin.class);
        assertThat(factory.create("TENANT_USER", JwtStpMode.STATELESS)).isInstanceOf(StpLogicJwtForStateless.class);
    }

    @Test
    void blankLoginTypeRejected() {
        assertThatThrownBy(() -> factory.create(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loginType");
        assertThatThrownBy(() -> factory.create(null, JwtStpMode.SIMPLE))
                .hasMessageContaining("loginType");
    }

    @Test
    void blankModeFallsBackToSimple() {
        assertThat(factory.create("APP", null)).isInstanceOf(StpLogicJwtForSimple.class);
    }

    @Test
    void simpleModeIssuesThreePartJwtWithLoginId() {
        StpLogic stpLogic = factory.create("PLATFORM_ADMIN");
        String token = stpLogic.createTokenValue("1001", "PC", 7200, Map.of("k1", "v1"));
        assertThat(token.split("\\.")).hasSize(3);
        var jwt = cn.slainc.crypto.jwt.JwtCodec.decodeUnverified(token);
        assertThat(jwt.algorithm()).isEqualTo("HS256");
        assertThat(String.valueOf(jwt.claims().get("loginId"))).isEqualTo("1001");
        assertThat(String.valueOf(jwt.claims().get("loginType"))).isEqualTo("PLATFORM_ADMIN");
    }
}
