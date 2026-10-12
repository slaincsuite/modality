package cn.slainc.modules.auth.token;

import cn.slainc.core.context.TenantContext;
import cn.slainc.modules.auth.AuthModuleApplication;
import cn.slainc.jwt.issue.JwtTokenService;
import cn.slainc.jwt.issue.SignedToken;
import cn.slainc.jwt.issue.TokenIssuanceSpec;
import cn.slainc.jwt.issue.TokenType;
import cn.slainc.oauth2.client.ClientAdminService;
import cn.slainc.oauth2.client.Oauth2ClientRecord;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AuthModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenLifecycleModuleTest {

    private static final String CLIENT_ID = "introspect-client";

    private static final String CLIENT_SECRET = "introspect-secret-001";

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ClientAdminService clientAdminService;

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
        registerClient();
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

        assertThat(introspectActive(accessToken)).isTrue();

        HttpResponse<String> revoke = post("/auth/token/revoke", "{\"token\":\"" + accessToken + "\"}");
        assertThat(revoke.statusCode()).as(revoke.body()).isEqualTo(200);

        assertThat(introspectActive(accessToken)).isFalse();
    }

    @Test
    void introspectRequiresClientAuthentication() throws Exception {
        SignedToken accessToken = jwtTokenService.issueToken(TokenIssuanceSpec.builder("9003")
                .ttl(Duration.ofMinutes(10)).build());

        HttpResponse<String> anonymous = post("/auth/token/introspect",
                "{\"token\":\"" + accessToken.tokenValue() + "\"}");
        assertThat(anonymous.statusCode()).as(anonymous.body()).isEqualTo(401);

        HttpResponse<String> wrongSecret = post("/auth/token/introspect", "{\"token\":\""
                + accessToken.tokenValue() + "\",\"clientId\":\"" + CLIENT_ID
                + "\",\"clientSecret\":\"wrong-secret\"}");
        assertThat(wrongSecret.statusCode()).as(wrongSecret.body()).isEqualTo(401);
    }

    @Test
    void revokeRejectsTamperedTokenAndKeepsOriginalValid() throws Exception {
        SignedToken accessToken = jwtTokenService.issueToken(TokenIssuanceSpec.builder("9004")
                .ttl(Duration.ofMinutes(10)).build());

        HttpResponse<String> tampered = post("/auth/token/revoke",
                "{\"token\":\"" + tamperPayload(accessToken.tokenValue()) + "\"}");
        assertThat(tampered.statusCode()).as(tampered.body()).isEqualTo(400);
        assertThat(tampered.body()).contains("令牌无效");

        assertThat(introspectActive(accessToken.tokenValue())).as("验签失败的吊销不能误伤原令牌").isTrue();
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

    private boolean introspectActive(String token) throws Exception {
        HttpResponse<String> introspect = post("/auth/token/introspect", "{\"token\":\"" + token
                + "\",\"clientId\":\"" + CLIENT_ID + "\",\"clientSecret\":\"" + CLIENT_SECRET + "\"}");
        assertThat(introspect.statusCode()).as(introspect.body()).isEqualTo(200);
        return Boolean.TRUE.equals(JsonPath.<Boolean>read(introspect.body(), "$.data.active"));
    }

    private String tamperPayload(String token) {
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        parts[1] = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + " ").getBytes(StandardCharsets.UTF_8));
        return String.join(".", parts);
    }

    private void registerClient() {
        try {
            clientAdminService.find(CLIENT_ID);
            return;
        } catch (IllegalArgumentException notFound) {
            Oauth2ClientRecord record = new Oauth2ClientRecord();
            record.setClientId(CLIENT_ID);
            record.setClientSecret(CLIENT_SECRET);
            record.setClientName("introspect 测试客户端");
            clientAdminService.register(record);
        }
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
