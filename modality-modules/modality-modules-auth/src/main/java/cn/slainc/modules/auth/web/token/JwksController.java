package cn.slainc.modules.auth.web.token;

import cn.slainc.jwt.jwks.JwksService;
import cn.slainc.web.result.NoWrap;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "JWKS 密钥", description = "JWT 签名公钥集查询")
@RestController
public class JwksController {

    private final JwksService jwksService;

    public JwksController(JwksService jwksService) {
        this.jwksService = jwksService;
    }

    @NoWrap
    @GetMapping(value = "/oauth2/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    public String jwks() {
        return jwksService.getJwksJson();
    }
}
