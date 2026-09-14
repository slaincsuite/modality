package cn.slainc.session.extend;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.SaTerminalInfo;
import cn.slainc.core.enums.LoginType;
import cn.slainc.security.StpKit;
import cn.slainc.security.stp.SecurityStpLogic;

import java.util.LinkedHashMap;
import java.util.Map;

public class SessionExtender {

    public Map<String, Object> extraData(LoginType type, String tokenValue) {
        SaTerminalInfo terminal = terminal(type, tokenValue);
        if (terminal == null || terminal.getExtraData() == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(terminal.getExtraData());
    }

    public Object getExtra(LoginType type, String tokenValue, String key) {
        SaTerminalInfo terminal = terminal(type, tokenValue);
        return terminal == null || key == null ? null : terminal.getExtra(key);
    }

    public boolean setExtra(LoginType type, String tokenValue, String key, Object value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("扩展字段名不能为空");
        }
        SessionTerminalHolder holder = holder(type, tokenValue);
        if (holder == null) {
            return false;
        }
        holder.terminal().setExtra(key, value);
        holder.session().update();
        return true;
    }

    public boolean setExtras(LoginType type, String tokenValue, Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("扩展字段不能为空");
        }
        SessionTerminalHolder holder = holder(type, tokenValue);
        if (holder == null) {
            return false;
        }
        values.forEach(holder.terminal()::setExtra);
        holder.session().update();
        return true;
    }

    public boolean removeExtra(LoginType type, String tokenValue, String key) {
        SessionTerminalHolder holder = holder(type, tokenValue);
        if (holder == null || holder.terminal().getExtraData() == null) {
            return false;
        }
        Object removed = holder.terminal().getExtraData().remove(key);
        if (removed == null) {
            return false;
        }
        holder.session().update();
        return true;
    }

    public SaTerminalInfo terminal(LoginType type, String tokenValue) {
        SessionTerminalHolder holder = holder(type, tokenValue);
        return holder == null ? null : holder.terminal();
    }

    private SessionTerminalHolder holder(LoginType type, String tokenValue) {
        if (type == null) {
            throw new IllegalArgumentException("账号体系不能为空");
        }
        if (tokenValue == null || tokenValue.isBlank()) {
            return null;
        }
        SecurityStpLogic logic = StpKit.of(type);
        Object loginId = logic.getLoginIdByToken(tokenValue);
        if (loginId == null) {
            return null;
        }
        SaSession session = logic.getSessionByLoginId(loginId, false);
        if (session == null) {
            return null;
        }
        SaTerminalInfo terminal = session.getTerminal(tokenValue);
        if (terminal == null) {
            return null;
        }
        return new SessionTerminalHolder(session, terminal);
    }

    private record SessionTerminalHolder(SaSession session, SaTerminalInfo terminal) {
    }
}
