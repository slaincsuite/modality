package cn.slainc.session.limit;

import cn.dev33.satoken.session.SaTerminalInfo;
import cn.slainc.core.enums.LoginType;
import cn.slainc.security.StpKit;
import cn.slainc.session.config.SessionConfigReader;
import cn.slainc.session.kick.KickSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

public class SessionLimitService {

    public static final String OVERFLOW_REASON = "单账号会话数量超过上限，自动踢出最早登录";

    private static final Logger log = LoggerFactory.getLogger(SessionLimitService.class);

    private final SessionConfigReader config;

    private final KickSessionService kickSessionService;

    public SessionLimitService(SessionConfigReader config, KickSessionService kickSessionService) {
        this.config = config;
        this.kickSessionService = kickSessionService;
    }

    public int enforce(LoginType type, Object loginId) {
        int max = maxSessionsPerUser();
        if (max <= 0 || type == null || loginId == null) {
            return 0;
        }
        List<SaTerminalInfo> terminals = StpKit.of(type).getTerminalListByLoginId(loginId);
        if (terminals == null || terminals.size() <= max) {
            return 0;
        }
        terminals.sort(Comparator.comparingLong(SaTerminalInfo::getCreateTime));
        int kicked = 0;
        while (terminals.size() > max) {
            SaTerminalInfo oldest = terminals.remove(0);
            log.warn("账号 {}（{} 体系）会话数量超过上限 {}，自动踢出最早登录的会话：设备类型={}，设备标识={}", loginId,
                    type.value(), max, oldest.getDeviceType(), oldest.getDeviceId());
            kickSessionService.kickoutByToken(type, oldest.getTokenValue(), OVERFLOW_REASON);
            kicked++;
        }
        return kicked;
    }

    public int maxSessionsPerUser() {
        return config.maxSessionsPerUser();
    }
}
