package cn.slainc.sensitive.integration;

import cn.slainc.crypto.digest.DigestUtils;
import cn.slainc.sensitive.demo.UserProfile;
import cn.slainc.utils.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = {SensitiveTestApplication.class, SensitiveDemoController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"modality.sensitive.mask.mask-char=#", "modality.sensitive.mask.hash-algorithm=SHA-256",
                "modality.sensitive.mask.replacement=***"})
class SensitiveConfigurationIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void appliesConfiguredMaskCharReplacementAndHashAlgorithm() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/sensitive/profile"))
                        .timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        String body = response.body();
        assertEquals("138####8888", read(body, "data.phone"));
        assertEquals("张#", read(body, "data.realName"));
        assertEquals("[已隐藏]", read(body, "data.replaceField"));
        assertEquals("***", read(body, "data.replaceDefaultField"));
        assertEquals(DigestUtils.sha256Hex(UserProfile.HASH_RAW), read(body, "data.hashField"));
        assertEquals("13######888", read(body, "data.customPhone"));
    }

    private static String read(String json, String path) {
        JsonNode node = JsonUtils.pathNode(json, path);
        assertNotNull(node, "JSON 中不存在路径 " + path + "，正文=" + json);
        return node.asString();
    }
}
