package cn.slainc.apikey;

import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.apikey.annotation.SaCheckApiKey;
import cn.slainc.core.result.Result;
import cn.slainc.core.jdbc.ClasspathSchemaInitializer;
import javax.sql.DataSource;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootConfiguration
@EnableAutoConfiguration
public class ApiKeyTestApplication {

    @Bean
    public ClasspathSchemaInitializer apiKeySchemaInitializer(DataSource dataSource) {
        return new ClasspathSchemaInitializer(dataSource, "api-key-schema.sql");
    }

    @RestController
    static class ApiKeyController {

        @GetMapping("/test/order-read")
        @SaCheckApiKey(scope = "order:read")
        public Result<String> orderRead() {
            return Result.ok("order-read-ok");
        }

        @GetMapping("/test/order-write")
        @SaCheckApiKey(scope = "order:write")
        public Result<String> orderWrite() {
            return Result.ok("order-write-ok");
        }

        @GetMapping("/test/order-any")
        @SaCheckApiKey(scope = {"order:read", "order:admin"}, mode = SaMode.OR)
        public Result<String> orderAny() {
            return Result.ok("order-any-ok");
        }
    }
}
