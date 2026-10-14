package cn.slainc.modules.infra.web.captcha;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.slainc.captcha.CaptchaChallenge;
import cn.slainc.captcha.CaptchaInput;
import cn.slainc.captcha.CaptchaService;
import cn.slainc.captcha.CaptchaType;
import cn.slainc.captcha.CaptchaTypeView;
import cn.slainc.captcha.CaptchaVerifyResult;
import cn.slainc.captcha.Channels;
import cn.slainc.core.constant.HttpHeaders;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.lang.BizAssert;
import cn.slainc.core.result.Result;
import cn.slainc.core.result.ResultCode;
import cn.slainc.utils.net.IpUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

// 验证码端点挂在库预留的 /captcha/** 白名单下，匿名可达：生成与校验自带一次性凭证语义
@Tag(name = "验证码", description = "图形验证码生成与校验")
@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @GetMapping("/types")
    public Result<List<CaptchaTypeView>> types() {
        List<CaptchaTypeView> views = new ArrayList<>();
        for (CaptchaType type : CaptchaType.values()) {
            views.add(new CaptchaTypeView(type.name(), type.getDescription(),
                    String.valueOf(type.behaviorBased())));
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
            input = CaptchaInput.ofSlide(parseCoordinate(body.get("slideX"), "slideX"));
        } else if (body.get("points") != null) {
            BizAssert.isTrue(body.get("points") instanceof List, "坐标点列表不合法");
            List<CaptchaInput.ClickPoint> points = new ArrayList<>();
            for (Object raw : (List<?>) body.get("points")) {
                BizAssert.isTrue(raw instanceof Map, "坐标点格式不合法");
                Map<?, ?> point = (Map<?, ?>) raw;
                points.add(new CaptchaInput.ClickPoint(
                        parseCoordinate(point.get("x"), "x"),
                        parseCoordinate(point.get("y"), "y")));
            }
            input = CaptchaInput.ofClick(points);
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
        return IpUtils.resolveClientIp(request.getHeader(HttpHeaders.FORWARDED_FOR),
                request.getHeader(HttpHeaders.REAL_IP),
                request.getHeader(HttpHeaders.CF_CONNECTING_IP),
                request.getRemoteAddr());
    }

    private static int parseCoordinate(Object value, String name) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new BizException(ResultCode.PARAM_MISSING, "坐标参数 " + name + " 必须是整数");
        }
    }
}
