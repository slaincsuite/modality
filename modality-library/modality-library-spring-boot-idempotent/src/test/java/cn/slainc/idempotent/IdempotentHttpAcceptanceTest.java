package cn.slainc.idempotent;

import cn.slainc.core.result.ResultCode;
import cn.slainc.utils.json.JsonUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = IdempotentTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdempotentHttpAcceptanceTest {

    @Autowired
    private IdempotentTestApplication.OrderService orderService;

    @Autowired
    private IdempotentTestApplication.TokenOrderService tokenOrderService;

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @BeforeAll
    static void requireRedis() {
        IdempotentRedisTestSupport.requireRedis();
    }

    @Test
    void concurrentHttpDuplicateExecutesOnce() throws Exception {
        orderService.resetBarrier();
        String orderNo = unique("HTTP-CONCURRENT-1");
        String json = JsonUtils.toJson(orderRequest(orderNo));
        int threads = 12;
        int before = orderService.slowExecutions().get();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return post("/demo/create-slow", json, Map.of());
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }));
        }
        start.countDown();
        int duplicates = 0;
        long deadline = System.currentTimeMillis() + 15_000L;
        while (System.currentTimeMillis() < deadline && duplicates < threads - 1) {
            duplicates = countDuplicateResponses(futures);
            Thread.sleep(20L);
        }
        orderService.releaseBarrier();
        List<HttpResponse<String>> responses = new ArrayList<>();
        for (Future<HttpResponse<String>> future : futures) {
            responses.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(orderService.slowExecutions().get() - before).as("HTTP 并发下真正执行的次数").isEqualTo(1);
        List<HttpResponse<String>> successes = responses.stream()
                .filter(response -> response.statusCode() == 200).toList();
        List<HttpResponse<String>> rejected = responses.stream()
                .filter(response -> response.statusCode() == 409).toList();
        assertThat(successes).hasSize(1);
        assertThat(rejected).hasSize(threads - 1);
        assertThat(JsonUtils.parseMap(successes.get(0).body()).get("data")).isEqualTo("慢订单-" + orderNo);
        Map<String, Object> rejectedBody = JsonUtils.parseMap(rejected.get(0).body());
        assertThat(rejectedBody.get("code")).isEqualTo(ResultCode.CONFLICT.code());
        assertThat(rejectedBody.get("message")).isEqualTo("请勿重复提交，请稍后再试");
    }

    @Test
    void interceptedHttpReplayReturnsFirstResult() throws Exception {
        String json = JsonUtils.toJson(orderRequest(unique("HTTP-CACHE-1")));
        int before = orderService.normalExecutions().get();
        HttpResponse<String> first = post("/demo/create", json, Map.of());
        HttpResponse<String> second = post("/demo/create", json, Map.of());
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(second.body()).as("必须返回首次结果的完整 Result 结构").isEqualTo(first.body());
        assertThat(orderService.normalExecutions().get() - before).isEqualTo(1);
    }

    @Test
    void tokenEndpointRegisteredByBeanWithoutComponentScan() throws Exception {
        HttpResponse<String> response = post("/idempotent/token",
                JsonUtils.toJson(Map.of("ttlSeconds", 60)), Map.of());
        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, Object> body = JsonUtils.parseMap(response.body());
        assertThat(body.get("code")).isEqualTo(ResultCode.SUCCESS.code());
        Map<String, Object> data = data(body);
        assertThat(String.valueOf(data.get("token"))).isNotBlank();
        assertThat(data.get("headerName")).isEqualTo("X-Idempotent-Token");
        assertThat(data.get("ttlSeconds")).isEqualTo(60);
    }

    @Test
    void tokenModeReplayReturnsFirstResult() throws Exception {
        String token = issueToken();
        String orderNo = unique("HTTP-TOKEN-1");
        int before = tokenOrderService.tokenExecutions().get();
        String json = JsonUtils.toJson(Map.of("token", token, "orderNo", orderNo));

        HttpResponse<String> first = post("/demo/submit-token", json, Map.of());
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(JsonUtils.parseMap(first.body()).get("data")).isEqualTo("Token订单-" + orderNo);

        HttpResponse<String> second = post("/demo/submit-token", json, Map.of());
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(second.body()).as("二次提交返回首次结果而不是再次执行").isEqualTo(first.body());

        HttpResponse<String> third = post("/demo/submit-token",
                JsonUtils.toJson(Map.of("token", token, "orderNo", unique("HTTP-TOKEN-2"))), Map.of());
        assertThat(third.statusCode()).as("已消费的 Token 不会再次执行").isEqualTo(200);
        assertThat(third.body()).as("已消费的 Token 换参数仍返回首次结果").isEqualTo(first.body());
        assertThat(tokenOrderService.tokenExecutions().get() - before).isEqualTo(1);
    }

    @Test
    void tokenInHeaderAlsoWorks() throws Exception {
        String token = issueToken();
        String orderNo = unique("HTTP-HEADER-1");
        int before = tokenOrderService.tokenExecutions().get();
        HttpResponse<String> response = post("/demo/submit-token",
                JsonUtils.toJson(Map.of("orderNo", orderNo)),
                Map.of("X-Idempotent-Token", token));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonUtils.parseMap(response.body()).get("data")).isEqualTo("Token订单-" + orderNo);
        assertThat(tokenOrderService.tokenExecutions().get() - before).isEqualTo(1);
    }

    @Test
    void httpRequestsWithDifferentParamsNotBlocked() throws Exception {
        String firstOrderNo = unique("HTTP-DIFF-1");
        String secondOrderNo = unique("HTTP-DIFF-2");
        int before = orderService.normalExecutions().get();
        HttpResponse<String> first = post("/demo/create", JsonUtils.toJson(orderRequest(firstOrderNo)), Map.of());
        HttpResponse<String> second = post("/demo/create", JsonUtils.toJson(orderRequest(secondOrderNo)), Map.of());
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(JsonUtils.parseMap(first.body()).get("data")).isEqualTo("订单-" + firstOrderNo);
        assertThat(JsonUtils.parseMap(second.body()).get("data")).isEqualTo("订单-" + secondOrderNo);
        assertThat(orderService.normalExecutions().get() - before).isEqualTo(2);
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String issueToken() throws Exception {
        HttpResponse<String> response = post("/idempotent/token", JsonUtils.toJson(Map.of("ttlSeconds", 120)), Map.of());
        assertThat(response.statusCode()).isEqualTo(200);
        return String.valueOf(data(JsonUtils.parseMap(response.body())).get("token"));
    }

    private Map<String, Object> orderRequest(String orderNo) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("orderNo", orderNo);
        request.put("amount", 100);
        request.put("timestamp", 1);
        return request;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> body) {
        return (Map<String, Object>) body.get("data");
    }

    private HttpResponse<String> post(String path, String json, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        headers.forEach(builder::header);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static int countDuplicateResponses(List<Future<HttpResponse<String>>> futures) {
        int count = 0;
        for (Future<HttpResponse<String>> future : futures) {
            if (!future.isDone()) {
                continue;
            }
            try {
                if (future.get().statusCode() == 409) {
                    count++;
                }
            } catch (Exception ignored) {
                // 未完成的请求继续等待
            }
        }
        return count;
    }
}