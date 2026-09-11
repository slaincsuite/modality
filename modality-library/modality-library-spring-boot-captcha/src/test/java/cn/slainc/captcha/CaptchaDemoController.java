package cn.slainc.captcha;

import cn.slainc.core.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CaptchaDemoController {

    private final CaptchaService captchaService;

    public CaptchaDemoController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @PostMapping("/captcha/image")
    public Result<CaptchaChallenge> image(@RequestBody CaptchaImageRequest request) {
        CaptchaType type = CaptchaType.of(request.type());
        CaptchaParams params = new CaptchaParams(request.width(), request.height(), request.length(), null, null);
        return Result.ok(captchaService.generate(request.channel(), type, params, request.clientIp()));
    }

    @PostMapping("/captcha/verify")
    public Result<CaptchaVerifyResult> verify(@RequestBody CaptchaVerifyRequest request) {
        CaptchaInput input = new CaptchaInput(request.code(), request.slideX(), request.points());
        return Result.ok(captchaService.verify(request.channel(), request.id(), input));
    }

    public record CaptchaImageRequest(String channel, String type, String clientIp,
                                      Integer width, Integer height, Integer length) {
    }

    public record CaptchaVerifyRequest(String channel, String id, String code, Integer slideX,
                                       List<CaptchaInput.ClickPoint> points) {
    }
}
