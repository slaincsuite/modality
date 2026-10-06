package cn.slainc.modules.audit.excel;

import cn.idev.excel.annotation.ExcelProperty;
import cn.slainc.excel.ExcelUtils;
import cn.slainc.session.SessionInfo;
import cn.slainc.utils.date.DateUtils;
import cn.slainc.utils.string.StringUtils;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;


public class OnlineUserExcelService {

    public void export(OutputStream outputStream, List<SessionInfo> sessions) {
        List<OnlineUserExportRow> rows = new ArrayList<>();
        for (SessionInfo session : sessions) {
            rows.add(new OnlineUserExportRow(session.loginType(), session.loginId(),
                    StringUtils.mask(session.tokenValue(), 4, 4),
                    session.deviceType(), session.deviceId(), session.clientIp(),
                    DateUtils.format(session.loginTime()),
                    DateUtils.format(session.lastActiveTime()),
                    session.remainingSeconds()));
        }
        ExcelUtils.write(outputStream, OnlineUserExportRow.class, rows, "在线用户");
    }

    public static class OnlineUserExportRow {

        @ExcelProperty("账号体系")
        private String loginType;

        @ExcelProperty("账号")
        private String loginId;

        @ExcelProperty("令牌")
        private String tokenValue;

        @ExcelProperty("设备类型")
        private String deviceType;

        @ExcelProperty("设备标识")
        private String deviceId;

        @ExcelProperty("登录IP")
        private String clientIp;

        @ExcelProperty("登录时间")
        private String loginTime;

        @ExcelProperty("最后活跃")
        private String lastActiveTime;

        @ExcelProperty("剩余有效期(秒)")
        private Long remainingSeconds;

        public OnlineUserExportRow() {
        }

        public OnlineUserExportRow(String loginType, String loginId, String tokenValue, String deviceType,
                                   String deviceId, String clientIp, String loginTime, String lastActiveTime,
                                   long remainingSeconds) {
            this.loginType = loginType;
            this.loginId = loginId;
            this.tokenValue = tokenValue;
            this.deviceType = deviceType;
            this.deviceId = deviceId;
            this.clientIp = clientIp;
            this.loginTime = loginTime;
            this.lastActiveTime = lastActiveTime;
            this.remainingSeconds = remainingSeconds;
        }

        public String getLoginType() {
            return loginType;
        }

        public void setLoginType(String loginType) {
            this.loginType = loginType;
        }

        public String getLoginId() {
            return loginId;
        }

        public void setLoginId(String loginId) {
            this.loginId = loginId;
        }

        public String getTokenValue() {
            return tokenValue;
        }

        public void setTokenValue(String tokenValue) {
            this.tokenValue = tokenValue;
        }

        public String getDeviceType() {
            return deviceType;
        }

        public void setDeviceType(String deviceType) {
            this.deviceType = deviceType;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getClientIp() {
            return clientIp;
        }

        public void setClientIp(String clientIp) {
            this.clientIp = clientIp;
        }

        public String getLoginTime() {
            return loginTime;
        }

        public void setLoginTime(String loginTime) {
            this.loginTime = loginTime;
        }

        public String getLastActiveTime() {
            return lastActiveTime;
        }

        public void setLastActiveTime(String lastActiveTime) {
            this.lastActiveTime = lastActiveTime;
        }

        public Long getRemainingSeconds() {
            return remainingSeconds;
        }

        public void setRemainingSeconds(Long remainingSeconds) {
            this.remainingSeconds = remainingSeconds;
        }
    }
}
