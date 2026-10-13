package cn.slainc.session.query;

import cn.dev33.satoken.session.SaTerminalInfo;
import cn.slainc.core.enums.LoginType;

public record RawTerminal(LoginType type, String loginId, String tokenValue, SaTerminalInfo terminal,
                          Long lastActiveMillis, Long remainingSeconds) {

    public RawTerminal(LoginType type, String loginId, String tokenValue, SaTerminalInfo terminal) {
        this(type, loginId, tokenValue, terminal, null, null);
    }
}
