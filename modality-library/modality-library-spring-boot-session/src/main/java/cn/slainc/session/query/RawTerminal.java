package cn.slainc.session.query;

import cn.dev33.satoken.session.SaTerminalInfo;
import cn.slainc.core.enums.LoginType;

public record RawTerminal(LoginType type, String loginId, String tokenValue, SaTerminalInfo terminal) {
}
