package cn.slainc.modules.auth.token;

import cn.slainc.core.context.TenantContext;
import cn.slainc.modules.auth.AuthModuleApplication;
import cn.slainc.jwt.issue.JwtTokenService;
import cn.slainc.jwt.issue.SignedToken;
import cn.slainc.jwt.issue.TokenIssuanceSpec;
import cn.slainc.jwt.issue.TokenType;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AuthModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenLifecycleModuleTest {

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private StringRedisTemplate redis;

    @LocalServerPort
    private int port;

    private HttpClient http;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        var keys = redis.keys("modality:jwt:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void refreshRotatesTokensAndRevocationInvalidatesAccess() throws Exception {
        SignedToken refreshToken = jwtTokenService.issueToken(TokenIssuanceSpec.builder("9001")
                .tokenType(TokenType.REFRESH).ttl(Duration.ofDays(1)).build());

        HttpResponse<String> refresh = post("/auth/token/refresh",
                "{\"token\":\"" + refreshToken.tokenValue() + "\"}");
        assertThat(refresh.statusCode()).as(refresh.body()).isEqualTo(200);
        String accessToken = JsonPath.<String>read(refresh.body(), "$.data.accessToken");
        String newRefresh = JsonPath.<String>read(refresh.body(), "$.data.refreshToken");
        assertThat(accessToken).isNotBlank();
        assertThat(newRefresh).isNotBlank();
        assertThat(newRefresh).isNotEqualTo(refreshToken.tokenValue());

        HttpResponse<String> introspect = post("/auth/token/introspect", "{\"token\":\"" + accessToken + "\"}");
        assertThat(introspect.statusCode()).as(introspect.body()).isEqualTo(200);
        assertThat((Boolean) JsonPath.<Boolean>read(introspect.body(), "$.data.active")).isTrue();

        HttpResponse<String> revoke = post("/auth/token/revoke", "{\"token\":\"" + accessToken + "\"}");
        assertThat(revoke.statusCode()).as(revoke.body()).isEqualTo(200);

        HttpResponse<String> afterRevoke = post("/auth/token/introspect", "{\"token\":\"" + accessToken + "\"}");
        assertThat(afterRevoke.statusCode()).as(afterRevoke.body()).isEqualTo(200);
        assertThat((Boolean) JsonPath.<Boolean>read(afterRevoke.body(), "$.data.active")).isFalse();
    }

    @Test
    void accessTokenCannotBeUsedToRefresh() throws Exception {
        SignedToken accessToken = jwtTokenService.issueToken(TokenIssuanceSpec.builder("9002")
                .tokenType(TokenType.ACCESS).ttl(Duration.ofMinutes(10)).build());
        HttpResponse<String> response = post("/auth/token/refresh",
                "{\"token\":\"" + accessToken.tokenValue() + "\"}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("不是刷新令牌");
    }

    @Test
    void garbageTokenIsRejectedWithBusinessError() throws Exception {
        HttpResponse<String> response = post("/auth/token/refresh", "{\"token\":\"not-a-jwt\"}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("无效或已过期");
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
