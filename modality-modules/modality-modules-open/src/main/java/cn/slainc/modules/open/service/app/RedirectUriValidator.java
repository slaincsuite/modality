package cn.slainc.modules.open.service.app;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;

import java.net.URI;
import java.util.List;

// 回调白名单严格校验：绝对 http(s) 地址、主机通配只允许 *.domain 前缀、路径通配只允许结尾 /*
public final class RedirectUriValidator {

    private RedirectUriValidator() {
    }

    public static void validate(List<String> redirectUris) {
        if (redirectUris == null || redirectUris.isEmpty()) {
            return;
        }
        for (String raw : redirectUris) {
            if (raw == null || raw.isBlank()) {
                throw new BizException(ResultCode.PARAM_MISSING, "回调地址列表存在空值");
            }
            String value = raw.trim();
            URI parsed;
            try {
                parsed = URI.create(value);
            } catch (IllegalArgumentException e) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR, "回调地址格式非法: " + value);
            }
            String scheme = parsed.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR,
                        "回调地址必须是 http/https 绝对地址: " + value);
            }
            // java.net.URI 对含通配符的主机返回 null，主机部分从 authority 手工提取
            String authority = parsed.getRawAuthority();
            String host = authority == null ? null : authority.split(":")[0];
            if (host == null || host.isBlank()) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR, "回调地址缺少主机名: " + value);
            }
            if (value.contains("@") || value.contains("#")) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR,
                        "回调地址不允许携带用户信息或锚点: " + value);
            }
            if (host.startsWith("*.")) {
                String rest = host.substring(2);
                if (rest.isBlank() || rest.contains("*")) {
                    throw new BizException(ResultCode.PARAM_BIND_ERROR,
                            "回调地址主机通配只允许 *.域名 形式: " + value);
                }
            } else if (host.contains("*")) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR,
                        "回调地址主机通配只允许 *.域名 形式: " + value);
            }
            String path = parsed.getRawPath();
            if (path != null && path.contains("*") && !path.endsWith("/*")) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR,
                        "回调地址路径通配只允许出现在结尾 /*: " + value);
            }
        }
    }
}
