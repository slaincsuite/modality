package cn.slainc.security.login;

public record LoginRequest(String username, String password, String deviceType, String deviceId, String clientIp) {

    public static LoginRequest of(String username, String password) {
        return new LoginRequest(username, password, null, null, null);
    }

    public LoginRequest withDeviceType(String deviceType) {
        return new LoginRequest(username, password, deviceType, deviceId, clientIp);
    }

    public LoginRequest withDeviceId(String deviceId) {
        return new LoginRequest(username, password, deviceType, deviceId, clientIp);
    }
}
