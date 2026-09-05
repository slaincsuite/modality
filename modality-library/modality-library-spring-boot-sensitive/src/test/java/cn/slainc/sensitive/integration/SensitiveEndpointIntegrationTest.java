package cn.slainc.sensitive.integration;

import cn.slainc.crypto.digest.Sm3Utils;
import cn.slainc.sensitive.core.SensitiveMasker;
import cn.slainc.sensitive.demo.UserProfile;
import cn.slainc.sensitive.jackson.SensitiveJacksonCustomizer;
import cn.slainc.sensitive.metrics.MicrometerSensitiveMetrics;
import cn.slainc.utils.json.JsonUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {SensitiveTestApplication.class, SensitiveDemoController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SensitiveEndpointIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    SensitiveMasker masker;

    @Autowired
    ApplicationContext context;

    @Autowired
    SimpleMeterRegistry meterRegistry;

    @Test
    void masksPhoneInRealHttpResponse() throws Exception {
        HttpResponse<String> response = get("/sensitive/profile");
        assertEquals(200, response.statusCode());
        assertNotNull(response.headers().firstValue("X-Trace-Id").orElse(null), "必须经过真实 Tomcat + Web 过滤器链");
        String body = response.body();
        System.out.println("[验收] GET /sensitive/profile -> " + response.statusCode() + " " + body);
        assertEquals("138****8888", read(body, "data.phone"));
        assertTrue(body.contains("138****8888"), body);
        assertFalse(body.contains(UserProfile.PHONE), "响应正文不得出现原始手机号：" + body);
    }

    @Test
    void keepsSourceObjectUnchangedAfterHttpSerialization() throws Exception {
        assertEquals(200, get("/sensitive/profile").statusCode());
        assertEquals(UserProfile.PHONE, SensitiveDemoController.PROFILE.getPhone());
        assertEquals(UserProfile.ID_CARD, SensitiveDemoController.PROFILE.getIdCard());
        assertEquals(UserProfile.BANK_CARD, SensitiveDemoController.PROFILE.getBankCard());
        assertEquals(UserProfile.EMAIL, SensitiveDemoController.PROFILE.getEmail());
        assertEquals(UserProfile.REAL_NAME, SensitiveDemoController.PROFILE.getRealName());
        assertEquals(UserProfile.ADDRESS, SensitiveDemoController.PROFILE.getAddress());
        assertEquals(UserProfile.PASSWORD, SensitiveDemoController.PROFILE.getPassword());
    }

    @Test
    void masksEverySensitiveTypeOverHttp() throws Exception {
        String body = get("/sensitive/profile").body();
        assertEquals("138****8888", read(body, "data.phone"));
        assertEquals("110101********8888", read(body, "data.idCard"));
        assertEquals("6222***********0123", read(body, "data.bankCard"));
        assertEquals("z*******@example.com", read(body, "data.email"));
        assertEquals("张*", read(body, "data.realName"));
        assertEquals("北京市朝阳区******", read(body, "data.address"));
        assertEquals("*".repeat(UserProfile.PASSWORD.length()), read(body, "data.password"));
        assertEquals("13######888", read(body, "data.customPhone"));
        assertEquals("无需脱敏", read(body, "data.plainField"));
    }

    @Test
    void appliesEveryStrategyOverHttp() throws Exception {
        String body = get("/sensitive/profile").body();
        assertEquals("[已隐藏]", read(body, "data.replaceField"));
        assertEquals("[已隐藏]", read(body, "data.replaceDefaultField"));
        assertEquals(Sm3Utils.digestHex(UserProfile.HASH_RAW), read(body, "data.hashField"));
        String encrypted = read(body, "data.encryptField");
        assertTrue(encrypted.startsWith("enc:v1:"), encrypted);
        assertEquals(UserProfile.ENCRYPT_RAW, masker.decrypt(encrypted));
        assertFalse(body.contains(UserProfile.ENCRYPT_RAW), body);
    }

    @Test
    void penetratesNestedStructuresOverHttp() throws Exception {
        String body = get("/sensitive/tree").body();
        assertEquals("137****5678", read(body, "data.departments[0].owner.mobile"));
        assertEquals("134****1111", read(body, "data.departments[0].members[1].mobile"));
        assertEquals("136****6666", read(body, "data.departmentArray[0].owner.mobile"));
        assertEquals("135****7777", read(body, "data.members[0].mobile"));
        assertEquals("134****8888", read(body, "data.departmentMap.research.owner.mobile"));
        assertEquals("136****2222", read(body, "data.departments[0].owner.backupPhones[0]"));
        assertEquals("135****4444", read(body, "data.departments[0].owner.contacts.home"));
        assertFalse(body.contains("13712345678"), body);
        assertFalse(body.contains("13477778888"), body);
    }

    @Test
    void doesNotLoopOnSelfCycleOverHttp() throws Exception {
        HttpResponse<String> response = get("/sensitive/cycle");
        assertEquals(200, response.statusCode());
        assertEquals("138****1111", read(response.body(), "data.phone"));
        assertTrue(response.body().contains("\"next\":null"), response.body());
        assertTrue(response.body().contains("\"peer\":null"), response.body());
    }

    @Test
    void doesNotLoopOnMutualCycleOverHttp() throws Exception {
        String body = get("/sensitive/mutual").body();
        assertEquals("138****2222", read(body, "data.phone"));
        assertEquals("138****3333", read(body, "data.peer.phone"));
        assertTrue(body.contains("\"peer\":null"), body);
    }

    @Test
    void masksGroupedFieldOnlyAfterSwitchingGroup() throws Exception {
        String inactive = get("/sensitive/profile").body();
        assertEquals(UserProfile.WORK_PHONE, read(inactive, "data.workPhone"));
        assertEquals("138****8888", read(inactive, "data.phone"));

        String active = get("/sensitive/profile", "X-Sensitive-Group", "contact").body();
        assertEquals("139****5678", read(active, "data.workPhone"));
        assertEquals("138****8888", read(active, "data.phone"));
    }

    @Test
    void skipsMaskingWhenThreadContextDisabled() throws Exception {
        String body = get("/sensitive/profile", "X-Sensitive-Bypass", "true").body();
        assertEquals(UserProfile.PHONE, read(body, "data.phone"));
        assertEquals(UserProfile.EMAIL, read(body, "data.email"));
        assertEquals(UserProfile.ID_CARD, read(body, "data.idCard"));
        assertEquals("138****8888", read(get("/sensitive/profile").body(), "data.phone"));
    }

    @Test
    void keepsWebLongToStringCustomization() throws Exception {
        String body = get("/sensitive/profile").body();
        assertEquals(String.valueOf(UserProfile.ID), read(body, "data.id"));
        assertTrue(body.contains("\"id\":\"1800000000000000001\""), body);
        assertTrue(context.getBeanNamesForType(JsonMapperBuilderCustomizer.class).length >= 2,
                "web 与本模块的 JsonMapperBuilderCustomizer 必须共存");
        assertNotNull(context.getBean(SensitiveJacksonCustomizer.class));
        assertNotNull(context.getBean(cn.slainc.web.serialize.JacksonCustomizer.class));
    }

    @Test
    void sanitizesScriptOverHttp() throws Exception {
        String body = get("/sensitive/xss").body();
        assertEquals("正常文本", read(body, "data.cleaned"));
        assertEquals("<p>段落</p>", read(body, "data.rich"));
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", read(body, "data.escaped"));
        assertFalse(body.contains("<script>"), body);
        assertFalse(body.contains("alert('xss')"), body);
    }

    @Test
    void recordsMaskMetricsOverHttp() throws Exception {
        assertNotNull(context.getBean(MicrometerSensitiveMetrics.class));
        get("/sensitive/profile");
        Counter counter = meterRegistry.find(MicrometerSensitiveMetrics.MASK_COUNTER)
                .tag("type", "PHONE")
                .tag("strategy", "MASK")
                .counter();
        assertNotNull(counter, "HTTP 响应脱敏应被计数");
        assertTrue(counter.count() > 0);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return get(path, null, null);
    }

    private HttpResponse<String> get(String path, String header, String value) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(15));
        if (header != null) {
            builder.header(header, value);
        }
        return HttpClient.newHttpClient().send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String read(String json, String path) {
        JsonNode node = JsonUtils.pathNode(json, path);
        assertNotNull(node, "JSON 中不存在路径 " + path + "，正文=" + json);
        return node.asString();
    }
}
