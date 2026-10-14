package cn.slainc.modules.auth.web.oauth2;

import cn.slainc.core.result.Result;
import cn.slainc.modules.auth.context.CurrentLogin;
import cn.slainc.oauth2.config.Oauth2Properties;
import cn.slainc.oauth2.consent.ConsentService;
import cn.slainc.security.LoginTypes;
import cn.slainc.security.StpKit;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;

// 授权确认页由 /oauth2/authorize 返回的 ConfirmView JSON 承担；这里补齐授权记录的自助查询与撤销
@Tag(name = "OAuth2 授权确认", description = "授权页确认与拒绝")
@RestController
@RequestMapping("/auth/oauth2")
public class OAuth2ConsentController {

    private final ConsentService consentService;

    private final Oauth2Properties oauth2Properties;

    public OAuth2ConsentController(ConsentService consentService, Oauth2Properties oauth2Properties) {
        this.consentService = consentService;
        this.oauth2Properties = oauth2Properties;
    }

    @GetMapping("/consents")
    public Result<List<Object>> consents(HttpServletRequest request) {
        var type = currentType();
        StpKit.of(type).checkLogin();
        return Result.ok(consentService.listByUser(String.valueOf(StpKit.of(type).getLoginId())).stream()
                .map(record -> (Object) record).toList());
    }

    @DeleteMapping("/consents/{clientId}")
    public Result<Void> revoke(@PathVariable String clientId, HttpServletRequest request) {
        var type = currentType();
        StpKit.of(type).checkLogin();
        consentService.revoke(String.valueOf(StpKit.of(type).getLoginId()), clientId);
        return Result.ok();
    }

    private cn.slainc.core.enums.LoginType currentType() {
        return LoginTypes.resolveOrDefault(oauth2Properties.getLoginType(),
                cn.slainc.core.enums.LoginType.TENANT_USER.value());
    }
}
