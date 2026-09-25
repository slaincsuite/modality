package cn.slainc.boot;

import cn.dev33.satoken.apikey.annotation.SaCheckApiKey;
import cn.slainc.core.result.Result;
import cn.slainc.sign.annotation.VerifySign;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// /oauth2/** 与 /sso/** 自批次 9 起由 modality-modules-auth 提供，本控制器只保留验收探针
@RestController
public class Batch5AcceptanceController {

    @SaCheckApiKey(scope = "order:read")
    @GetMapping("/batch5/api/order")
    public Result<String> order(@RequestParam(defaultValue = "1") String orderId) {
        return Result.ok("订单 " + orderId + " 读取成功");
    }

    @VerifySign(appId = "boot-app")
    @GetMapping("/batch5/sign/order")
    public Result<String> signedOrder(@RequestParam(defaultValue = "1") String orderId) {
        return Result.ok("签名订单 " + orderId + " 处理成功");
    }
}
