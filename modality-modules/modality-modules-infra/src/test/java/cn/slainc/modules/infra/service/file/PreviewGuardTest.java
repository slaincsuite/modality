package cn.slainc.modules.infra.service.file;

import cn.slainc.core.exception.BizException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreviewGuardTest {

    @Test
    void htmlAndSvgAreRejected() {
        assertThatThrownBy(() -> PreviewGuard.requireInlineAllowed("text/html"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不允许在线预览");
        assertThatThrownBy(() -> PreviewGuard.requireInlineAllowed("image/svg+xml; charset=UTF-8"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不允许在线预览");
        assertThat(PreviewGuard.requireInlineAllowed("IMAGE/PNG")).isEqualTo("image/png");
    }

    @Test
    void pngMagicMustMatchDeclaredType() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        PreviewGuard.assertMagicMatches("image/png", png);

        assertThatThrownBy(() -> PreviewGuard.assertMagicMatches("image/png",
                "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("与声明的类型不一致");
    }

    @Test
    void gifAndWebpMagicAreChecked() {
        PreviewGuard.assertMagicMatches("image/gif", "GIF89a\u0001\u0002".getBytes(StandardCharsets.ISO_8859_1));
        byte[] webp = new byte[16];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, webp, 8, 4);
        PreviewGuard.assertMagicMatches("image/webp", webp);

        assertThatThrownBy(() -> PreviewGuard.assertMagicMatches("image/webp", new byte[16]))
                .isInstanceOf(BizException.class);
    }

    @Test
    void textPlainRejectsMarkupContent() {
        PreviewGuard.assertMagicMatches("text/plain", "纯文本内容".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> PreviewGuard.assertMagicMatches("text/plain",
                "<svg onload=alert(1)></svg>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("与声明的类型不一致");
    }
}
