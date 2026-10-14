package cn.slainc.modules.auth.web.qrcode;

import cn.slainc.modules.auth.service.qrcode.QrLoginService;

import cn.slainc.core.result.Result;
import cn.slainc.modules.auth.context.CurrentLogin;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "扫码登录", description = "二维码生成与扫码状态轮询")
@RestController
public class QrLoginController {

    private final QrLoginService qrLoginService;

    public QrLoginController(QrLoginService qrLoginService) {
        this.qrLoginService = qrLoginService;
    }

    public record ExchangeBody(String deviceType, String deviceId) {
    }

    @PostMapping("/auth/qrcode")
    public Result<QrLoginService.QrTicketBundle> create() {
        return Result.ok(qrLoginService.create());
    }

    @GetMapping("/auth/qrcode/{ticketId}")
    public Result<QrLoginService.QrStatusView> status(@PathVariable String ticketId) {
        return Result.ok(qrLoginService.status(ticketId));
    }

    @PostMapping("/auth/qrcode/{ticketId}/refresh")
    public Result<QrLoginService.QrTicketBundle> refresh(@PathVariable String ticketId) {
        return Result.ok(qrLoginService.refresh(ticketId));
    }

    @PostMapping("/auth/qrcode/{ticketId}/scan")
    public Result<Void> scan(@PathVariable String ticketId, HttpServletRequest request) {
        CurrentLogin.requireLogin(request);
        var type = CurrentLogin.type(request);
        qrLoginService.scan(ticketId, type, typeLoginId(type));
        return Result.ok();
    }

    @PostMapping("/auth/qrcode/{ticketId}/confirm")
    public Result<Void> confirm(@PathVariable String ticketId, HttpServletRequest request) {
        CurrentLogin.requireLogin(request);
        var type = CurrentLogin.type(request);
        qrLoginService.confirm(ticketId, type, typeLoginId(type));
        return Result.ok();
    }

    @PostMapping("/auth/qrcode/{ticketId}/cancel")
    public Result<Void> cancel(@PathVariable String ticketId, HttpServletRequest request) {
        CurrentLogin.requireLogin(request);
        var type = CurrentLogin.type(request);
        qrLoginService.cancel(ticketId, type, typeLoginId(type));
        return Result.ok();
    }

    @PostMapping("/auth/qrcode/{ticketId}/exchange")
    public Result<Object> exchange(@PathVariable String ticketId,
                                   @RequestHeader(value = CurrentLogin.TYPE_HEADER, required = false) String type,
                                   @RequestBody(required = false) ExchangeBody body) {
        String deviceType = body == null ? null : body.deviceType();
        String deviceId = body == null ? null : body.deviceId();
        return Result.ok(qrLoginService.exchange(ticketId, CurrentLogin.type(type), deviceType, deviceId));
    }

    private Object typeLoginId(cn.slainc.core.enums.LoginType type) {
        return cn.slainc.security.StpKit.of(type).getLoginId();
    }
}
