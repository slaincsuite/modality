package cn.slainc.jwt;

import cn.slainc.jwt.jwks.JwksService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 端点由测试应用自行注册（§6.6），库本身不向宿主注册任何 HTTP 端点
@RestController
public class JwksController {

    private final JwksService jwksService;

    public JwksController(JwksService jwksService) {
        this.jwksService = jwksService;
    }

    @GetMapping("/oauth2/jwks")
    public String jwks() {
        return jwksService.getJwksJson();
    }
}
