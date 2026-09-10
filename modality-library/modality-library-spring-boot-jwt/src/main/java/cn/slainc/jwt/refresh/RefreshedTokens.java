package cn.slainc.jwt.refresh;

import cn.slainc.jwt.issue.SignedToken;

public record RefreshedTokens(SignedToken accessToken, SignedToken refreshToken) {
}
