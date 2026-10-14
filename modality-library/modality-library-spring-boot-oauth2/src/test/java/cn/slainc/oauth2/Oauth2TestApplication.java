package cn.slainc.oauth2;

import cn.dev33.satoken.util.SaResult;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.result.Result;
import cn.slainc.oauth2.userinfo.Oauth2UserInfoService;
import cn.slainc.oauth2.protocol.Oauth2ServerDelegate;
import cn.slainc.crypto.hash.BCryptUtils;
import cn.slainc.security.login.LoginRequest;
import cn.slainc.security.login.SecurityLoginService;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.PasswordAccount;
import cn.slainc.web.result.NoWrap;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootConfiguration
@EnableAutoConfiguration
public class Oauth2TestApplication {

    @Bean
    public AccountAuthProvider testAccountProvider() {
        return new TestAccountProvider();
    }

    static class TestAccountProvider implements AccountAuthProvider {

        private final Map<String, PasswordAccount> accounts = new ConcurrentHashMap<>();

        TestAccountProvider() {
            accounts.put("TENANT_USER:user", new PasswordAccount(2001L, "user",
                    BCryptUtils.hash("Password#123"), true, LocalDateTime.now().minusDays(1), false));
            accounts.put("TENANT_USER:alice", new PasswordAccount(2002L, "alice",
                    BCryptUtils.hash("Password#123"), true, LocalDateTime.now().minusDays(1), false));
        }

        @Override
        public PasswordAccount findAccount(String username, LoginType type) {
            return accounts.get(type.value() + ":" + username);
        }

        @Override
        public PasswordAccount findAccountByUserId(Long userId, LoginType type) {
            return accounts.values().stream()
                    .filter(account -> account.userId() != null && account.userId().equals(userId))
                    .findFirst().orElse(null);
        }
    }

    @RestController
    @NoWrap
    static class Oauth2Controller {

        private final Oauth2ServerDelegate delegate;

        private final Oauth2UserInfoService userInfoService;

        Oauth2Controller(Oauth2ServerDelegate delegate, Oauth2UserInfoService userInfoService) {
            this.delegate = delegate;
            this.userInfoService = userInfoService;
        }

        @RequestMapping("/oauth2/**")
        public Object oauth2() {
            return delegate.handle();
        }

        @GetMapping("/test/userinfo")
        public Object userInfo(@RequestParam("access_token") String accessToken) {
            return userInfoService.userInfo(accessToken);
        }
    }

    @RestController
    static class LoginController {

        private final SecurityLoginService loginService;

        LoginController(SecurityLoginService loginService) {
            this.loginService = loginService;
        }

        @PostMapping("/test/login")
        public Result<Object> login(@RequestBody Map<String, String> body) {
            return Result.ok(loginService.login(LoginType.TENANT_USER,
                    LoginRequest.of(body.get("username"), body.get("password"))));
        }
    }

    @RestController
    @NoWrap
    static class RawController {

        @GetMapping("/test/raw")
        public SaResult raw() {
            return SaResult.ok("原始返回");
        }
    }
}
