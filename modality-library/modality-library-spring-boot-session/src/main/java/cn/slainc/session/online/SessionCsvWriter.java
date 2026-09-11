package cn.slainc.session.online;

import cn.slainc.session.SessionInfo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SessionCsvWriter {

    public static final String BOM = "\uFEFF";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] HEADERS = {"账号体系", "登录账号", "Token", "设备类型", "设备分类", "设备标识", "客户端IP",
            "User-Agent", "登录时间", "最后活跃时间", "剩余有效期(秒)"};

    public String write(List<SessionInfo> sessions) {
        StringBuilder builder = new StringBuilder(BOM);
        builder.append(String.join(",", HEADERS)).append('\n');
        if (sessions != null) {
            for (SessionInfo session : sessions) {
                builder.append(cell(session.loginType())).append(',')
                        .append(cell(session.loginId())).append(',')
                        .append(cell(session.tokenValue())).append(',')
                        .append(cell(session.deviceType())).append(',')
                        .append(cell(session.deviceCategory())).append(',')
                        .append(cell(session.deviceId())).append(',')
                        .append(cell(session.clientIp())).append(',')
                        .append(cell(session.userAgent())).append(',')
                        .append(cell(format(session.loginTime()))).append(',')
                        .append(cell(format(session.lastActiveTime()))).append(',')
                        .append(session.remainingSeconds()).append('\n');
            }
        }
        return builder.toString();
    }

    private String format(LocalDateTime time) {
        return time == null ? "" : TIME_FORMAT.format(time);
    }

    private String cell(String value) {
        String text = value == null ? "" : value;
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
