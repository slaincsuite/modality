package cn.slainc.modules.infra.web;

import cn.slainc.captcha.CaptchaChallenge;
import cn.slainc.captcha.CaptchaInput;
import cn.slainc.captcha.CaptchaService;
import cn.slainc.captcha.CaptchaType;
import cn.slainc.captcha.CaptchaVerifyResult;
import cn.slainc.captcha.Channels;
import cn.slainc.core.lang.BizAssert;
import cn.slainc.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 验证码端点挂在库预留的 /captcha/** 白名单下，匿名可达：生成与校验自带一次性凭证语义
@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @GetMapping("/types")
    public Result<List<Map<String, String>>> types() {
        List<Map<String, String>> views = new ArrayList<>();
        for (CaptchaType type : CaptchaType.values()) {
            Map<String, String> view = new LinkedHashMap<>();
            view.put("name", type.name());
            view.put("label", type.getDescription());
            view.put("behaviorBased", String.valueOf(type.behaviorBased()));
            views.add(view);
        }
        return Result.ok(views);
    }

    @PostMapping("/generate")
    public Result<CaptchaChallenge> generate(@RequestBody(required = false) Map<String, Object> body,
                                             HttpServletRequest request) {
        String channel = body == null || body.get("channel") == null ? Channels.WEB
                : Channels.normalize(String.valueOf(body.get("channel")));
        String type = body == null || body.get("type") == null ? null : String.valueOf(body.get("type"));
        CaptchaChallenge challenge = captchaService.generate(channel, CaptchaType.of(type), clientIp(request));
        return Result.ok(challenge);
    }

    @PostMapping("/verify")
    public Result<Map<String, Object>> verify(@RequestBody Map<String, Object> body) {
        BizAssert.hasText(String.valueOf(body.get("captchaId")), "验证码编号不能为空");
        String channel = body.get("channel") == null ? Channels.WEB
                : Channels.normalize(String.valueOf(body.get("channel")));
        CaptchaInput input;
        if (body.get("slideX") != null) {
            input = CaptchaInput.ofSlide(Integer.parseInt(String.valueOf(body.get("slideX"))));
        } else if (body.get("points") != null) {
            List<Map<String, Object>> points = (List<Map<String, Object>>) body.get("points");
            input = CaptchaInput.ofClick(points.stream()
                    .map(point -> new CaptchaInput.ClickPoint(
                            Integer.parseInt(String.valueOf(point.get("x"))),
                            Integer.parseInt(String.valueOf(point.get("y")))))
                    .toList());
        } else {
            input = CaptchaInput.ofCode(body.get("code") == null ? null : String.valueOf(body.get("code")));
        }
        CaptchaVerifyResult result = captchaService.verify(channel, String.valueOf(body.get("captchaId")), input);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("success", result.success());
        view.put("message", result.message());
        return Result.ok(view);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
