package cn.slainc.modules.auth.oauth2;

import cn.slainc.oauth2.web.Oauth2ServerDelegate;
import cn.slainc.web.result.NoWrap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
