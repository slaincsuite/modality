package cn.slainc.web.autoconfigure;

import cn.slainc.web.body.RequestBodyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(classes = ServerDefaultsOverrideIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"server.compression.enabled=false", "spring.servlet.multipart.max-file-size=3KB",
                "modality.web.request-body.max-file-size=1KB"})
class ServerDefaultsOverrideIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Autowired
    ServerProperties serverProperties;

    @Autowired
    MultipartProperties multipartProperties;

    @Autowired
    RequestBodyProperties requestBodyProperties;

    @Test
    void explicitServerPropertiesWinOverModalityDefaults() {
        assertFalse(serverProperties.getCompression().getEnabled(), "显式 server.compression.enabled 应优先生效");
        assertEquals(3072L, multipartProperties.getMaxFileSize().toBytes(), "显式 multipart 配置应优先生效");
        assertEquals(1024L, requestBodyProperties.getMaxFileSize().toBytes());
        assertEquals(10 * 1024 * 1024L, multipartProperties.getMaxRequestSize().toBytes());
    }
}
