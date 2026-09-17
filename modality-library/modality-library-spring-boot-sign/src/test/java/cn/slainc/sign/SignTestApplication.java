package cn.slainc.sign;

import cn.slainc.core.result.Result;
import cn.slainc.sign.annotation.VerifySign;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@SpringBootConfiguration
@EnableAutoConfiguration
public class SignTestApplication {

    @RestController
    static class OrderController {

        @GetMapping("/test/order")
        @VerifySign
        public Result<Map<String, String>> order(@RequestParam("orderNo") String orderNo,
                                                 @RequestParam(value = "amount", required = false) String amount) {
            return Result.ok(Map.of("orderNo", orderNo));
        }

        @PostMapping("/test/submit")
        @VerifySign(appId = "fixed-app", version = 1, windowSeconds = 600)
        public Result<Map<String, String>> submit(@RequestParam("orderNo") String orderNo) {
            return Result.ok(Map.of("orderNo", orderNo));
        }
    }
}
