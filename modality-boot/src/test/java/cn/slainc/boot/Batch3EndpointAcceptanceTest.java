package cn.slainc.boot;

import cn.slainc.core.result.Result;
import cn.slainc.idempotent.annotation.Idempotent;
import cn.slainc.idempotent.annotation.IdempotentStrategy;
import cn.slainc.sensitive.annotation.Sensitive;
import cn.slainc.sensitive.annotation.SensitiveType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "modality.security.web.enabled=false")
class Batch3EndpointAcceptanceTest {

    private static final String RAW_PHONE = "13812348888";

    // 幂等指纹是稳定哈希，固定参数会被上一轮遗留的结果缓存回放，故每轮用唯一值
    private static final String RUN_ID = java.util.UUID.randomUUID().toString();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @LocalServerPort
    private int port;

    @Autowired
    private Batch3AcceptanceController controller;

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30));
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body, StandardCharsets.UTF_8));
        } else {
            builder.GET();
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("M3 验收：响应 JSON 中手机号脱敏为 138****8888")
    void sensitiveMasksPhoneInRealHttpResponse() throws Exception {
        HttpResponse<String> response = send("GET", "/batch3/profile", null);
        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("\"phone\":\"138****8888\""), "响应里没有 138****8888，实际: " + body);
        assertFalse(body.contains(RAW_PHONE), "响应里仍能看到原始手机号: " + body);
        assertTrue(body.contains("\"idCard\":\"110101********1234\""), "身份证未脱敏: " + body);
        assertTrue(body.contains("\"nickname\":\"张三\""), "未标注的字段不应被改动: " + body);
        assertEquals(RAW_PHONE, controller.rawPhone(), "序列化不能修改内存中的对象");
    }

    @Test
    @DisplayName("M3 验收：重复提交不重复执行，直接回放首次结果")
    void idempotentReplaysFirstResult() throws Exception {
        String payload = "{\"orderNo\":\"M3-SEQUENTIAL-" + RUN_ID + "\"}";
        int before = controller.executions();
        HttpResponse<String> first = send("POST", "/batch3/order", payload);
        HttpResponse<String> second = send("POST", "/batch3/order", payload);
        assertEquals(200, first.statusCode(), "首次提交应成功: " + first.body());
        assertEquals(200, second.statusCode(), "重复提交应回放首次结果: " + second.body());
        assertEquals(before + 1, controller.executions(), "业务方法被执行了不止一次");
        assertEquals(first.body(), second.body(), "重复请求应返回与首次一致的结果");
    }

    @Test
    @DisplayName("M3 验收：并发重复提交被拦截，业务方法只执行一次")
    void idempotentBlocksConcurrentDuplicate() throws Exception {
        String payload = "{\"orderNo\":\"M3-CONCURRENT-" + RUN_ID + "\"}";
        int before = controller.executions();
        int concurrency = 8;
        CyclicBarrier barrier = new CyclicBarrier(concurrency);
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            Callable<HttpResponse<String>> task = () -> {
                barrier.await();
                return send("POST", "/batch3/slow-order", payload);
            };
            List<Future<HttpResponse<String>>> futures = pool.invokeAll(
                    java.util.Collections.nCopies(concurrency, task));
            long success = 0;
            long rejected = 0;
            for (Future<HttpResponse<String>> future : futures) {
                int status = future.get().statusCode();
                if (status == 200) {
                    success++;
                } else {
                    rejected++;
                }
            }
            assertEquals(1, success, "并发重复提交应只有一次成功");
            assertEquals(concurrency - 1, rejected, "其余请求应被拦截");
            assertEquals(before + 1, controller.executions(), "业务方法被执行了不止一次");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("M3 验收：不同参数不会被误拦截（负向对照）")
    void idempotentAllowsDifferentParameters() throws Exception {
        int before = controller.executions();
        HttpResponse<String> a = send("POST", "/batch3/order", "{\"orderNo\":\"M3-NEG-A-" + RUN_ID + "\"}");
        HttpResponse<String> b = send("POST", "/batch3/order", "{\"orderNo\":\"M3-NEG-B-" + RUN_ID + "\"}");
        assertEquals(200, a.statusCode(), a.body());
        assertEquals(200, b.statusCode(), b.body());
        assertEquals(before + 2, controller.executions(), "不同参数应各自执行");
    }
}

@RestController
@RequestMapping("/batch3")
class Batch3AcceptanceController {

    private final AtomicInteger executions = new AtomicInteger();

    private final Profile profile = new Profile();

    @GetMapping("/profile")
    Profile profile() {
        return profile;
    }

    String rawPhone() {
        return profile.getPhone();
    }

    @PostMapping("/order")
    @Idempotent(strategy = IdempotentStrategy.PARAMETER, ttl = 120L, message = "请勿重复提交")
    Result<String> createOrder(@RequestBody OrderRequest request) {
        executions.incrementAndGet();
        return Result.ok("订单已创建: " + request.orderNo());
    }

    @PostMapping("/slow-order")
    @Idempotent(strategy = IdempotentStrategy.PARAMETER, ttl = 120L, message = "请勿重复提交")
    Result<String> createSlowOrder(@RequestBody OrderRequest request) throws InterruptedException {
        executions.incrementAndGet();
        Thread.sleep(400L);
        return Result.ok("订单已创建: " + request.orderNo());
    }

    int executions() {
        return executions.get();
    }

    record OrderRequest(String orderNo) {
    }

    static class Profile {

        @Sensitive(type = SensitiveType.PHONE)
        private final String phone = "13812348888";

        @Sensitive(type = SensitiveType.ID_CARD)
        private final String idCard = "110101199003071234";

        private final String nickname = "张三";

        public String getPhone() {
            return phone;
        }

        public String getIdCard() {
            return idCard;
        }

        public String getNickname() {
            return nickname;
        }
    }
}
