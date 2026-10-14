package cn.slainc.modules.auth.web.oauth2;

import cn.slainc.oauth2.protocol.Oauth2ServerDelegate;
import cn.slainc.web.result.NoWrap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "OAuth2 协议", description = "令牌签发与授权码协议端点")
@RestController
public class OAuth2ProtocolController {

    private final Oauth2ServerDelegate oauth2Delegate;

    public OAuth2ProtocolController(Oauth2ServerDelegate oauth2Delegate) {
        this.oauth2Delegate = oauth2Delegate;
    }

    @NoWrap
    @RequestMapping("/oauth2/**")
    public Object oauth2() {
        return oauth2Delegate.handle();
    }
}
