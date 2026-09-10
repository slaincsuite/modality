package cn.slainc.jwt;

import cn.slainc.crypto.jwt.Jwk;
import cn.slainc.crypto.jwt.JwkSet;
import cn.slainc.crypto.jwt.JwtCodec;
import cn.slainc.jwt.issue.SignedToken;
import cn.slainc.jwt.issue.TokenIssuanceSpec;
import cn.slainc.jwt.issue.JwtTokenService;
import cn.slainc.jwt.jwks.JwksService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = JwtTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "modality.jwt.keys[0].kid=acceptance-rsa",
                "modality.jwt.keys[0].algorithm=RS256",
                "modality.jwt.keys[1].kid=acceptance-ec",
                "modality.jwt.keys[1].algorithm=ES256",
                "modality.security.web.enabled=false"
        })
class JwtHttpAcceptanceTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwksService jwksService;

    @Autowired
    private JwtTokenService tokenService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @BeforeAll
    static void requireRedis() {
        JwtRedisTestSupport.requireRedis();
        org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory factory =
                JwtRedisTestSupport.newConnectionFactory();
        org.springframework.data.redis.core.StringRedisTemplate template =
                JwtRedisTestSupport.newStringRedisTemplate(factory);
        JwtRedisTestSupport.cleanJwtKeys(template);
        factory.destroy();
    }

    @Test
    void 真实HTTP拉取JWKS返回200且含公钥() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/oauth2/jwks")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        JwkSet jwkSet = JwkSet.parse(response.body());
        assertThat(jwkSet.size()).isEqualTo(2);
        Jwk rsa = jwkSet.requireByKid("acceptance-rsa");
        assertThat(rsa.kty()).isEqualTo("RSA");
        assertThat(rsa.toMap()).containsKeys("n", "e");
        Jwk ec = jwkSet.requireByKid("acceptance-ec");
        assertThat(ec.kty()).isEqualTo("EC");
        assertThat(ec.crv()).isEqualTo("P-256");
        assertThat(ec.toMap()).containsKeys("x", "y");
        for (Jwk jwk : jwkSet.keys()) {
            assertThat(jwk.use()).isEqualTo("sig");
            assertThat(jwk.isPrivate()).as("对外 JWKS 绝不能包含私钥参数").isFalse();
        }
    }

    @Test
    void JWKS端点响应与服务层输出一致() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/oauth2/jwks")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.body()).isEqualTo(jwksService.getJwksJson());
    }

    @Test
    void JWKS公钥可以验签服务签发的令牌() throws Exception {
        SignedToken signed = tokenService.issueToken(TokenIssuanceSpec.builder("user-http")
                .audience("web")
                .claims(Map.of("tenantId", "t-1"))
                .build());

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/oauth2/jwks")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Jwk jwk = JwkSet.parse(response.body()).requireByKid(signed.kid());
        boolean verified = JwtCodec.verify(signed.tokenValue(), jwk.toPublicKey());
        assertThat(verified).as("JWKS 导出的公钥必须能验签真实签发的令牌").isTrue();
    }
}
