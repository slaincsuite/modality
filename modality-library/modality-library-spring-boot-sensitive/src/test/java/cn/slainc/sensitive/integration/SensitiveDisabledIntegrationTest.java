package cn.slainc.sensitive.integration;

import cn.slainc.sensitive.demo.UserProfile;
import cn.slainc.sensitive.jackson.SensitiveJacksonCustomizer;
import cn.slainc.utils.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "modality.sensitive.enabled=false")
class SensitiveDisabledIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext context;

    @Test
    void keepsRawValuesWhenModuleDisabled() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/sensitive/profile"))
                        .timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        String body = response.body();
        assertEquals(UserProfile.PHONE, read(body, "data.phone"));
        assertEquals(UserProfile.EMAIL, read(body, "data.email"));
        assertTrue(body.contains(UserProfile.PHONE), body);
        assertFalse(body.contains("138****8888"), body);
    }

    @Test
    void doesNotRegisterJacksonCustomizerWhenDisabled() {
        assertEquals(0, context.getBeanNamesForType(SensitiveJacksonCustomizer.class).length);
        assertFalse(context.containsBean("sensitiveJacksonCustomizer"));
    }

    private static String read(String json, String path) {
        JsonNode node = JsonUtils.pathNode(json, path);
        assertNotNull(node, "JSON 中不存在路径 " + path + "，正文=" + json);
        return node.asString();
    }
}
