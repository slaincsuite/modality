package cn.slainc.jwt;

import cn.slainc.jwt.jwks.JwksService;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootConfiguration
@EnableAutoConfiguration
public class JwtTestApplication {

    @Bean
    public JwksController jwksController(JwksService jwksService) {
        return new JwksController(jwksService);
    }
}
