package cn.slainc.session.query;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.SaTerminalInfo;
import cn.slainc.core.enums.LoginType;
import cn.slainc.security.StpKit;
import cn.slainc.security.config.SecurityConfigReader;
import cn.slainc.security.stp.SecurityStpLogic;
import cn.slainc.session.SessionInfo;
import cn.slainc.session.config.SessionConfigReader;
import cn.slainc.session.device.DeviceTypeNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SessionScanner {

    private static final Logger log = LoggerFactory.getLogger(SessionScanner.class);

    private static final long NOT_VALUE_EXPIRE = -2L;

    private final SessionConfigReader config;

    private final SecurityConfigReader securityConfig;

    private final SessionTokenBatchReader batchReader;

    public SessionScanner(SessionConfigReader config, SecurityConfigReader securityConfig) {
        this(config, securityConfig, null);
    }

    public SessionScanner(SessionConfigReader config, SecurityConfigReader securityConfig,
                          SessionTokenBatchReader batchReader) {
        this.config = config;
        this.securityConfig = securityConfig;
        this.batchReader = batchReader;
    }

    public RawScanOutcome scanRaw(LoginType type) {
        if (type == null) {
            throw new IllegalArgumentException("账号体系不能为空");
        }
        SecurityStpLogic logic = StpKit.of(type);
        int maxScan = config.maxScan();
        List<String> keys;
        try {
            keys = logic.searchTokenValue("", 0, maxScan + 1, true);
        } catch (Exception e) {
            log.warn("扫描 {} 体系在线会话失败：{}", type.value(), e.getMessage());
            return new RawScanOutcome(type, List.of(), false, null, 0);
        }
        if (keys == null || keys.isEmpty()) {
            return new RawScanOutcome(type, List.of(), false, null, 0);
        }
        boolean truncated = keys.size() > maxScan;
        List<String> limited = truncated ? new ArrayList<>(keys.subList(0, maxScan)) : keys;
        String prefix = logic.splicingKeyTokenValue("");
        Map<String, String> loginIds = new HashMap<>();
        Map<String, Long> lastActiveTimes = new HashMap<>();
        Map<String, Long> remainingSeconds = new HashMap<>();
        boolean batched = batchLoad(logic, limited, prefix, loginIds, lastActiveTimes, remainingSeconds);
        Map<String, SaSession> sessionCache = new HashMap<>();
        List<RawTerminal> terminals = new ArrayList<>(limited.size());
        for (String key : limited) {
            String tokenValue = SessionTokens.strip(key, prefix);
            String loginIdKey = batched ? loginIds.get(logic.splicingKeyTokenValue(tokenValue))
                    : loginIdOf(logic, tokenValue);
            if (loginIdKey == null || loginIdKey.isBlank()) {
                continue;
            }
            SaSession session = sessionCache.get(loginIdKey);
            if (session == null && !sessionCache.containsKey(loginIdKey)) {
                session = logic.getSessionByLoginId(loginIdKey, false);
                sessionCache.put(loginIdKey, session);
            }
            if (session == null) {
                continue;
            }
            SaTerminalInfo terminal = session.getTerminal(tokenValue);
            if (terminal == null) {
                continue;
            }
            Long lastActive = batched ? lastActiveTimes.getOrDefault(logic.splicingKeyLastActiveTime(tokenValue),
                    NOT_VALUE_EXPIRE) : null;
            Long remaining = batched ? remainingSeconds.getOrDefault(logic.splicingKeyTokenValue(tokenValue),
                    NOT_VALUE_EXPIRE) : null;
            terminals.add(new RawTerminal(type, loginIdKey, tokenValue, terminal, lastActive, remaining));
        }
        String message = truncated ? config.truncatedMessage(maxScan) : null;
        return new RawScanOutcome(type, terminals, truncated, message, limited.size());
    }

    public List<RawScanOutcome> scanRawAll(LoginType type) {
        List<RawScanOutcome> outcomes = new ArrayList<>();
        for (LoginType item : typesOf(type)) {
            outcomes.add(scanRaw(item));
        }
        return outcomes;
    }

    public SessionScanOutcome scan(LoginType type) {
        RawScanOutcome raw = scanRaw(type);
        List<SessionInfo> sessions = new ArrayList<>(raw.terminals().size());
        for (RawTerminal terminal : raw.terminals()) {
            sessions.add(describe(terminal.type(), terminal.loginId(), terminal.terminal(), terminal.tokenValue(),
                    true, terminal.lastActiveMillis(), terminal.remainingSeconds()));
        }
        return new SessionScanOutcome(raw.type(), sessions, raw.truncated(), raw.message(), raw.scanned());
    }

    public List<SessionScanOutcome> scanAll(LoginType type) {
        List<SessionScanOutcome> outcomes = new ArrayList<>();
        for (LoginType item : typesOf(type)) {
            outcomes.add(scan(item));
        }
        return outcomes;
    }

    public List<LoginType> typesOf(LoginType type) {
        return type == null ? List.of(LoginType.PLATFORM_ADMIN, LoginType.TENANT_USER, LoginType.APP) : List.of(type);
    }

    public SessionInfo describe(LoginType type, String loginId, SaTerminalInfo terminal, String tokenValue,
                                boolean active) {
        return describe(type, loginId, terminal, tokenValue, active, null, null);
    }

    public SessionInfo describe(LoginType type, String loginId, SaTerminalInfo terminal, String tokenValue,
                                boolean active, Long lastActiveMillis, Long remainingSeconds) {
        SecurityStpLogic logic = StpKit.of(type);
        Map<String, Object> extra = terminal.getExtraData() == null ? new LinkedHashMap<>()
                : new LinkedHashMap<>(terminal.getExtraData());
        long createTime = terminal.getCreateTime() > 0 ? terminal.getCreateTime() : System.currentTimeMillis();
        long loginTime = asLong(extra.get("loginTime"), createTime);
        long lastActiveTime = loginTime;
        if (lastActiveMillis != null) {
            if (lastActiveMillis > 0L) {
                lastActiveTime = lastActiveMillis;
            }
        } else {
            try {
                long stored = logic.getTokenLastActiveTime(tokenValue);
                if (stored > 0) {
                    lastActiveTime = stored;
                }
            } catch (Exception e) {
                log.debug("读取 token 最后活跃时间失败：{}", e.getMessage());
            }
        }
        long remaining = NOT_VALUE_EXPIRE;
        if (remainingSeconds != null) {
            remaining = remainingSeconds;
        } else {
            try {
                remaining = logic.getTokenTimeout(tokenValue);
            } catch (Exception e) {
                log.debug("读取 token 剩余有效期失败：{}", e.getMessage());
            }
        }
        String rawDeviceType = terminal.getDeviceType();
        String userAgent = asString(extra.get("ua"));
        String displayed = config.maskToken() ? SessionTokens.mask(tokenValue) : tokenValue;
        return new SessionInfo(type.value(), loginId, displayed, rawDeviceType,
                DeviceTypeNormalizer.normalize(rawDeviceType, userAgent), asString(extra.get("deviceId")),
                asString(extra.get("ip")), userAgent, toDateTime(loginTime), toDateTime(lastActiveTime),
                securityConfig.sessionTimeout(type), remaining, active, extra);
    }

    public static LocalDateTime toDateTime(long epochMillis) {
        return epochMillis <= 0 ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    public static long asLong(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean batchLoad(SecurityStpLogic logic, List<String> limited, String prefix,
                              Map<String, String> loginIds, Map<String, Long> lastActiveTimes,
                              Map<String, Long> remainingSeconds) {
        if (batchReader == null) {
            return false;
        }
        List<String> tokenKeys = new ArrayList<>(limited.size());
        List<String> lastActiveKeys = new ArrayList<>(limited.size());
        for (String key : limited) {
            String tokenValue = SessionTokens.strip(key, prefix);
            if (tokenValue == null || tokenValue.isBlank()) {
                continue;
            }
            tokenKeys.add(logic.splicingKeyTokenValue(tokenValue));
            lastActiveKeys.add(logic.splicingKeyLastActiveTime(tokenValue));
        }
        try {
            loginIds.putAll(batchReader.getMany(tokenKeys));
            remainingSeconds.putAll(batchReader.getTimeouts(tokenKeys));
            for (Map.Entry<String, String> entry : batchReader.getMany(lastActiveKeys).entrySet()) {
                long parsed = parseMillis(entry.getValue());
                if (parsed > 0L) {
                    lastActiveTimes.put(entry.getKey(), parsed);
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("批量读取会话 token 失败，回退逐条读取：{}", e.getMessage());
            loginIds.clear();
            lastActiveTimes.clear();
            remainingSeconds.clear();
            return false;
        }
    }

    private String loginIdOf(SecurityStpLogic logic, String tokenValue) {
        try {
            Object loginId = logic.getLoginIdByToken(tokenValue);
            return loginId == null ? null : String.valueOf(loginId);
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseMillis(String value) {
        if (value == null || value.isBlank()) {
            return NOT_VALUE_EXPIRE;
        }
        int comma = value.indexOf(',');
        String text = comma < 0 ? value : value.substring(0, comma);
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return NOT_VALUE_EXPIRE;
        }
    }
}
