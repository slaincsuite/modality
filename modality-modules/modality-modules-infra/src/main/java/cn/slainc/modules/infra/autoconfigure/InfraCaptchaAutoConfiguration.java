package cn.slainc.modules.infra.autoconfigure;

import cn.slainc.captcha.CaptchaService;
import cn.slainc.modules.infra.web.captcha.CaptchaController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

@AutoConfiguration(afterName = "cn.slainc.captcha.autoconfigure.CaptchaAutoConfiguration")
@ConditionalOnBean(CaptchaService.class)
@ConditionalOnProperty(prefix = "modality.infra", name = "enabled", matchIfMissing = true)
@Import(CaptchaController.class)
public class InfraCaptchaAutoConfiguration {
}
