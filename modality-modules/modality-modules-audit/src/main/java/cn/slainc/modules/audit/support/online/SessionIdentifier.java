package cn.slainc.modules.audit.support.online;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class SessionIdentifier {

    private static final String PREFIX = "sid_";

    private static final String ALGORITHM = "SHA-256";

    private SessionIdentifier() {
    }

    public static String of(LoginType type, String tokenValue) {
        if (type == null || tokenValue == null || tokenValue.isBlank()) {
            throw new BizException("账号体系与会话令牌不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            digest.update(type.value().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(tokenValue.getBytes(StandardCharsets.UTF_8));
            return PREFIX + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new BizException("会话标识生成失败");
        }
    }

    public static boolean matches(String sessionId, LoginType type, String tokenValue) {
        if (sessionId == null || sessionId.isBlank() || type == null || tokenValue == null
                || tokenValue.isBlank()) {
            return false;
        }
        byte[] expected = of(type, tokenValue).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = sessionId.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }
}
