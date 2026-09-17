package cn.slainc.sso;

import cn.dev33.satoken.sso.util.SaSsoConsts;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.result.Result;
import cn.slainc.crypto.hash.BCryptUtils;
import cn.slainc.security.login.LoginRequest;
import cn.slainc.security.login.SecurityLoginService;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.PasswordAccount;
import cn.slainc.sso.web.SsoClientDelegate;
import cn.slainc.sso.web.SsoServerDelegate;
import cn.slainc.security.StpKit;
import cn.slainc.web.result.NoWrap;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootConfiguration
@EnableAutoConfiguration
public class SsoTestApplication {

    @Bean
    public AccountAuthProvider testAccountProvider() {
        return new AccountAuthProvider() {

            private final Map<String, PasswordAccount> accounts = new ConcurrentHashMap<>();

            {
                accounts.put("USER:user", new PasswordAccount(2001L, "user",
                        BCryptUtils.hash("Password#123"), true, LocalDateTime.now().minusDays(1), false));
            }

            @Override
            public PasswordAccount findAccount(String username, LoginType type) {
                return accounts.get(type.value() + ":" + username);
            }
        };
    }

    @RestController
    @NoWrap
    static class SsoController {

        private final SsoServerDelegate serverDelegate;

        private final SsoClientDelegate clientDelegate;

        SsoController(SsoServerDelegate serverDelegate, SsoClientDelegate clientDelegate) {
            this.serverDelegate = serverDelegate;
            this.clientDelegate = clientDelegate;
        }

        @RequestMapping("/sso/**")
        public Object sso() {
            Object result = serverDelegate.handle();
            if (SaSsoConsts.NOT_HANDLE.equals(result)) {
                result = clientDelegate.handle();
            }
            return result;
        }

        @GetMapping("/sso/isLogin")
        public Object isLogin() {
            return StpKit.client().isLogin();
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
            return Result.ok(loginService.login(LoginType.USER,
                    LoginRequest.of(body.get("username"), body.get("password"))));
        }
    }
}
