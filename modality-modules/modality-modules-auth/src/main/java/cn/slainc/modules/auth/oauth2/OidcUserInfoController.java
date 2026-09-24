package cn.slainc.modules.auth.oauth2;

import cn.slainc.oauth2.userinfo.Oauth2UserInfoService;
import cn.slainc.web.result.NoWrap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OidcUserInfoController {

    private final Oauth2UserInfoService userInfoService;

    public OidcUserInfoController(Oauth2UserInfoService userInfoService) {
        this.userInfoService = userInfoService;
    }

    @NoWrap
    @GetMapping("/oauth2/userinfo")
    public Map<String, Object> userInfo(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @RequestParam(value = "access_token", required = false) String accessToken) {
        String token = accessToken;
        if ((token == null || token.isBlank()) && authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring(7);
        }
        return userInfoService.userInfo(token);
    }
}
