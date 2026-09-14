package cn.slainc.oauth2;

import cn.slainc.oauth2.client.ClientAdminService;
import cn.slainc.oauth2.client.Oauth2ClientRecord;
import cn.slainc.oauth2.consent.ConsentService;
import cn.slainc.oauth2.pkce.PkceService;
import cn.slainc.oauth2.state.StateService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Oauth2TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Oauth2HttpAcceptanceTest {

    private static final String WEB_CLIENT_ID = "web-app";

    private static final String WEB_CLIENT_SECRET = "web-secret-001";

    private static final String OIDC_CLIENT_ID = "oidc-app";

    private static final String OIDC_CLIENT_SECRET = "oidc-secret-001";

    private static final String PASSWORD = "Password#123";

    @Autowired
    private ClientAdminService clientAdminService;

    @Autowired
    private StateService stateService;

    @Autowired
    private PkceService pkceService;

    @Autowired
    private ConsentService consentService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private HttpClient httpClient;

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5)).build();
        deleteByPrefix("modality:oauth2:");
        deleteByPrefix("modality:security:");
        deleteByPrefix("satoken:");
        registerDefaultClients();
    }

    private void deleteByPrefix(String prefix) {
        var keys = redisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private void registerDefaultClients() {
        Oauth2ClientRecord webClient = new Oauth2ClientRecord();
        webClient.setClientId(WEB_CLIENT_ID);
        webClient.setClientSecret(WEB_CLIENT_SECRET);
        webClient.setClientName("测试 Web 应用");
        webClient.setContractScopes(List.of("openid", "userinfo", "pay"));
        webClient.setAllowRedirectUris(List.of("http://callback.example.com/cb", "http://callback.example.com/*"));
        webClient.setAllowGrantTypes(List.of("authorization_code", "implicit", "password", "client_credentials",
                "refresh_token"));
        clientAdminService.remove(WEB_CLIENT_ID);
        clientAdminService.register(webClient);

        Oauth2ClientRecord oidcClient = new Oauth2ClientRecord();
        oidcClient.setClientId(OIDC_CLIENT_ID);
        oidcClient.setClientSecret(OIDC_CLIENT_SECRET);
        oidcClient.setClientName("测试 OIDC 应用");
        oidcClient.setContractScopes(List.of("openid", "userinfo"));
        oidcClient.setAllowRedirectUris(List.of("http://oidc.example.com/cb"));
        oidcClient.setAllowGrantTypes(List.of("authorization_code"));
        clientAdminService.remove(OIDC_CLIENT_ID);
        clientAdminService.register(oidcClient);
    }

    @Test
    void authorizationCodeModeFullFlow() throws Exception {
        String token = loginUser("user");
        String state = stateService.issue();
        HttpResponse<String> authorize = get("/oauth2/authorize?response_type=code"
                + "&client_id=" + WEB_CLIENT_ID
                + "&redirect_uri=" + encode("http://callback.example.com/cb")
                + "&scope=" + encode("openid userinfo")
                + "&state=" + state, token);
        assertThat(authorize.statusCode()).as(authorize.body()).isEqualTo(302);
        String location = authorize.headers().firstValue("Location").orElse("");
        assertThat(location).contains("code=");
        assertThat(location).contains("state=" + state);
        assertThat(stateService.validateOnce(state)).isTrue();
        assertThat(stateService.validateOnce(state)).isFalse();
        String code = paramFromUrl(location, "code");

        HttpResponse<String> tokenResponse = postForm("/oauth2/token",
                "grant_type=authorization_code&code=" + code
                        + "&redirect_uri=" + encode("http://callback.example.com/cb")
                        + "&client_id=" + WEB_CLIENT_ID + "&client_secret=" + WEB_CLIENT_SECRET);
        assertOk(tokenResponse);
        assertThat(JsonPath.<String>read(tokenResponse.body(), "$.access_token")).isNotBlank();
        String refreshToken = JsonPath.read(tokenResponse.body(), "$.refresh_token");
        assertThat(refreshToken).isNotBlank();

        HttpResponse<String> reuse = postForm("/oauth2/token",
                "grant_type=authorization_code&code=" + code
                        + "&redirect_uri=" + encode("http://callback.example.com/cb")
                        + "&client_id=" + WEB_CLIENT_ID + "&client_secret=" + WEB_CLIENT_SECRET);
        assertThat(reuse.statusCode()).isEqualTo(200);
        assertThat((int) JsonPath.<Integer>read(reuse.body(), "$.code")).isNotEqualTo(200);
        assertThat((String) JsonPath.<String>read(reuse.body(), "$.message")).contains("code");

        HttpResponse<String> refresh = postForm("/oauth2/refresh",
                "grant_type=refresh_token&client_id=" + WEB_CLIENT_ID
                        + "&client_secret=" + WEB_CLIENT_SECRET + "&refresh_token=" + refreshToken);
        assertOk(refresh);
        assertThat(JsonPath.<String>read(refresh.body(), "$.access_token")).isNotBlank();
    }

    @Test
    void tokenEndpointSupportsBasicAuth() throws Exception {
        String token = loginUser("user");
        String code = obtainCode(token, "http://callback.example.com/cb", "openid", null, null);
        String basic = Base64.getEncoder()
                .encodeToString((WEB_CLIENT_ID + ":" + WEB_CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> response = postFormWithHeader("/oauth2/token",
                "grant_type=authorization_code&code=" + code
                        + "&redirect_uri=" + encode("http://callback.example.com/cb"),
                "Authorization", "Basic " + basic);
        assertOk(response);
        assertThat(JsonPath.<String>read(response.body(), "$.access_token")).isNotBlank();
    }

    @Test
    void implicitModeReturnsTokenInFragment() throws Exception {
        String token = loginUser("user");
        HttpResponse<String> authorize = get("/oauth2/authorize?response_type=token"
                + "&client_id=" + WEB_CLIENT_ID
                + "&redirect_uri=" + encode("http://callback.example.com/cb")
                + "&scope=" + encode("openid") + "&state=xyz", token);
        assertThat(authorize.statusCode()).as(authorize.body()).isEqualTo(302);
        String location = authorize.headers().firstValue("Location").orElse("");
        assertThat(location).contains("token=");
    }

    @Test
    void passwordGrantIssuesToken() throws Exception {
        HttpResponse<String> response = postForm("/oauth2/token",
                "grant_type=password&username=user&password=" + encode(PASSWORD)
                        + "&scope=" + encode("openid userinfo")
                        + "&client_id=" + WEB_CLIENT_ID + "&client_secret=" + WEB_CLIENT_SECRET);
        assertOk(response);
        assertThat(JsonPath.<String>read(response.body(), "$.access_token")).isNotBlank();
    }

    @Test
    void clientCredentialsGrantIssuesClientToken() throws Exception {
        HttpResponse<String> response = postForm("/oauth2/token",
                "grant_type=client_credentials&scope=" + encode("userinfo")
                        + "&client_id=" + WEB_CLIENT_ID + "&client_secret=" + WEB_CLIENT_SECRET);
        assertOk(response);
        assertThat(response.body()).containsPattern("\\\"(access_token|client_token)\\\":\\\"[^\\\"]+");
    }

    @Test
    void pkceVerifierMustMatchChallenge() throws Exception {
        String token = loginUser("user");
        String verifier = pkceService.randomVerifier();
        String challenge = pkceService.challengeOf(PkceService.METHOD_S256, verifier);
        String code = obtainCode(token, "http://callback.example.com/cb", "openid", challenge,
                PkceService.METHOD_S256);

        HttpResponse<String> wrong = postForm("/oauth2/token",
                "grant_type=authorization_code&code=" + code
                        + "&redirect_uri=" + encode("http://callback.example.com/cb")
                        + "&code_verifier=" + encode(pkceService.randomVerifier())
                        + "&client_id=" + WEB_CLIENT_ID + "&client_secret=" + WEB_CLIENT_SECRET);
        assertThat((int) JsonPath.<Integer>read(wrong.body(), "$.code")).isNotEqualTo(200);
        assertThat((String) JsonPath.<String>read(wrong.body(), "$.message")).contains("PKCE");

        HttpResponse<String> right = postForm("/oauth2/token",
                "grant_type=authorization_code&code=" + code
                        + "&redirect_uri=" + encode("http://callback.example.com/cb")
                        + "&code_verifier=" + encode(verifier)
                        + "&client_id=" + WEB_CLIENT_ID + "&client_secret=" + WEB_CLIENT_SECRET);
        assertOk(right);
        assertThat(JsonPath.<String>read(right.body(), "$.access_token")).isNotBlank();
    }

    @Test
    void oidcIssuesIdTokenAndUserInfo() throws Exception {
        String token = loginUser("user");
        String code = obtainCode(token, "http://oidc.example.com/cb", "openid userinfo", null, null, "nonce-abc-123");
        HttpResponse<String> response = postForm("/oauth2/token",
                "grant_type=authorization_code&code=" + code
                        + "&redirect_uri=" + encode("http://oidc.example.com/cb")
                        + "&client_id=" + OIDC_CLIENT_ID + "&client_secret=" + OIDC_CLIENT_SECRET);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        String idToken = JsonPath.read(response.body(), "$.id_token");
        assertThat(idToken).isNotBlank();
        String payload = new String(Base64.getUrlDecoder().decode(
                idToken.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"nonce\":\"nonce-abc-123\"");
        assertThat(payload).contains("\"aud\":\"" + OIDC_CLIENT_ID + "\"");

        String accessToken = JsonPath.read(response.body(), "$.access_token");
        HttpResponse<String> userInfo = get("/test/userinfo?access_token=" + encode(accessToken), null);
        assertThat(userInfo.statusCode()).as(userInfo.body()).isEqualTo(200);
        assertThat(userInfo.body()).contains("\"sub\"");
        assertThat(userInfo.body()).contains("\"openid\"");
    }

    @Test
    void highRiskScopeRequiresConfirmAndConsentIsRecorded() throws Exception {
        String token = loginUser("user");
        HttpResponse<String> first = get("/oauth2/authorize?response_type=code"
                + "&client_id=" + WEB_CLIENT_ID
                + "&redirect_uri=" + encode("http://callback.example.com/cb")
                + "&scope=" + encode("openid pay") + "&state=s1", token);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body()).contains("需要您确认授权");
        assertThat(first.body()).contains("pay");

        HttpResponse<String> confirm = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/oauth2/doConfirm"))
                .header("satoken", token)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "response_type=code&client_id=" + WEB_CLIENT_ID
                                + "&redirect_uri=" + encode("http://callback.example.com/cb")
                                + "&scope=" + encode("openid pay") + "&build_redirect_uri=true"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(confirm.statusCode()).as(confirm.body()).isEqualTo(200);
        String redirectUri = JsonPath.read(confirm.body(), "$.redirect_uri");
        assertThat(redirectUri).contains("code=");
        assertThat(consentService.listByUser("2001"))
                .anySatisfy(record -> {
                    assertThat(record.getClientId()).isEqualTo(WEB_CLIENT_ID);
                    assertThat(record.getScopes()).contains("pay");
                    assertThat(record.active()).isTrue();
                });

        HttpResponse<String> second = get("/oauth2/authorize?response_type=code"
                + "&client_id=" + WEB_CLIENT_ID
                + "&redirect_uri=" + encode("http://callback.example.com/cb")
                + "&scope=" + encode("openid pay") + "&state=s2", token);
        assertThat(second.statusCode()).as(second.body()).isEqualTo(302);

        consentService.revoke("2001", WEB_CLIENT_ID);
        HttpResponse<String> third = get("/oauth2/authorize?response_type=code"
                + "&client_id=" + WEB_CLIENT_ID
                + "&redirect_uri=" + encode("http://callback.example.com/cb")
                + "&scope=" + encode("openid pay") + "&state=s3", token);
        assertThat(third.statusCode()).isEqualTo(200);
        assertThat(third.body()).contains("需要您确认授权");
    }

    @Test
    void redirectUriMustBeWhitelistedButWildcardAllowed() throws Exception {
        String token = loginUser("user");
        HttpResponse<String> rejected = get("/oauth2/authorize?response_type=code"
                + "&client_id=" + WEB_CLIENT_ID
                + "&redirect_uri=" + encode("http://evil.example.net/cb")
                + "&scope=openid&state=s1", token);
        assertThat((int) JsonPath.<Integer>read(rejected.body(), "$.code")).isNotEqualTo(200);

        HttpResponse<String> wildcard = get("/oauth2/authorize?response_type=code"
                + "&client_id=" + WEB_CLIENT_ID
                + "&redirect_uri=" + encode("http://callback.example.com/wild")
                + "&scope=openid&state=s2", token);
        assertThat(wildcard.statusCode()).as(wildcard.body()).isEqualTo(302);
    }

    @Test
    void uncontractedScopeIsRejected() throws Exception {
        Oauth2ClientRecord limited = new Oauth2ClientRecord();
        limited.setClientId("limited-app");
        limited.setClientSecret("limited-secret");
        limited.setContractScopes(List.of("openid"));
        limited.setAllowRedirectUris(List.of("http://limited.example.com/cb"));
        limited.setAllowGrantTypes(List.of("password"));
        clientAdminService.register(limited);

        HttpResponse<String> response = postForm("/oauth2/token",
                "grant_type=password&username=user&password=" + encode(PASSWORD)
                        + "&scope=" + encode("openid pay")
                        + "&client_id=limited-app&client_secret=limited-secret");
        assertThat((int) JsonPath.<Integer>read(response.body(), "$.code")).isNotEqualTo(200);
        assertThat((String) JsonPath.<String>read(response.body(), "$.message")).contains("签约");
    }

    @Test
    void newlyRegisteredClientWorksWithoutRestart() throws Exception {
        Oauth2ClientRecord fresh = new Oauth2ClientRecord();
        fresh.setClientId("hot-app");
        fresh.setClientSecret("hot-secret");
        fresh.setContractScopes(List.of("openid"));
        fresh.setAllowGrantTypes(List.of("password"));
        clientAdminService.register(fresh);

        HttpResponse<String> response = postForm("/oauth2/token",
                "grant_type=password&username=user&password=" + encode(PASSWORD)
                        + "&scope=openid&client_id=hot-app&client_secret=hot-secret");
        assertOk(response);
        assertThat(JsonPath.<String>read(response.body(), "$.access_token")).isNotBlank();
    }

    @Test
    void disabledClientIsRejected() throws Exception {
        clientAdminService.changeStatus(WEB_CLIENT_ID, false);
        HttpResponse<String> response = postForm("/oauth2/token",
                "grant_type=password&username=user&password=" + encode(PASSWORD)
                        + "&scope=openid&client_id=" + WEB_CLIENT_ID + "&client_secret=" + WEB_CLIENT_SECRET);
        assertThat((int) JsonPath.<Integer>read(response.body(), "$.code")).isNotEqualTo(200);
        assertThat((String) JsonPath.<String>read(response.body(), "$.message")).contains("client_id");
    }

    private String loginUser(String username) throws Exception {
        HttpResponse<String> response = postJson("/test/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.data.tokenValue");
    }

    private String obtainCode(String token, String redirectUri, String scope, String challenge,
                              String method) throws Exception {
        return obtainCode(token, redirectUri, scope, challenge, method, null);
    }

    private String obtainCode(String token, String redirectUri, String scope, String challenge,
                              String method, String nonce) throws Exception {
        String clientId = redirectUri.contains("oidc.example.com") ? OIDC_CLIENT_ID : WEB_CLIENT_ID;
        StringBuilder url = new StringBuilder("/oauth2/authorize?response_type=code")
                .append("&client_id=").append(clientId)
                .append("&redirect_uri=").append(encode(redirectUri))
                .append("&scope=").append(encode(scope))
                .append("&state=st");
        if (challenge != null) {
            url.append("&code_challenge=").append(encode(challenge));
        }
        if (method != null) {
            url.append("&code_challenge_method=").append(method);
        }
        if (nonce != null) {
            url.append("&nonce=").append(encode(nonce));
        }
        HttpResponse<String> authorize = get(url.toString(), token);
        assertThat(authorize.statusCode()).as(authorize.body()).isEqualTo(302);
        String location = authorize.headers().firstValue("Location").orElse("");
        return paramFromUrl(location, "code");
    }

    private String paramFromUrl(String url, String name) {
        int queryIndex = url.indexOf('?');
        String query = queryIndex >= 0 ? url.substring(queryIndex + 1) : url;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private void assertOk(HttpResponse<String> response) {
        assertThat((int) JsonPath.<Integer>read(response.body(), "$.code")).as(response.body()).isEqualTo(200);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl() + path)).GET();
        if (token != null) {
            builder.header("satoken", token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(String path, String form) throws Exception {
        return postFormWithHeader(path, form, null, null);
    }

    private HttpResponse<String> postFormWithHeader(String path, String form, String headerName,
                                                    String headerValue) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        if (headerName != null) {
            builder.header(headerName, headerValue);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
