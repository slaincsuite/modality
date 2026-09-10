package cn.slainc.captcha.support;

import com.wf.captcha.SpecCaptcha;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class CaptchaFonts {

    private CaptchaFonts() {
    }

    public static Font builtin(int fontIndex, float size) {
        try {
            SpecCaptcha helper = new SpecCaptcha();
            helper.setFont(fontIndex, size);
            return helper.getFont();
        } catch (IOException exception) {
            throw new UncheckedIOException("加载验证码内置字体失败", exception);
        } catch (FontFormatException exception) {
            throw new IllegalStateException("验证码内置字体格式无效", exception);
        }
    }

    public static Font cjk(int style, float size) {
        String sample = "验证码点击文字滑块";
        String[] candidates = {"楷体", "Kaiti SC", "STKaiti", "Songti SC", "SimSun", "PingFang SC"};
        for (String name : candidates) {
            Font candidate = new Font(name, style, Math.round(size));
            if (candidate.canDisplayUpTo(sample) == -1) {
                return candidate.deriveFont(size);
            }
        }
        return new Font(Font.SANS_SERIF, style, Math.round(size)).deriveFont(size);
    }
}
