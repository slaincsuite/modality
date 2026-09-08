package cn.slainc.security.login;

public record LoginResult(String tokenName, String tokenValue, Long loginId, String loginType, long tokenTimeout,
                          boolean mustChangePassword) {
}
