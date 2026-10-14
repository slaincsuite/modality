package cn.slainc.mfa.authentication;

import cn.slainc.core.context.UserContext;

import java.time.Duration;

public interface SecondFactorGate {

    boolean required(UserContext.UserInfo user);

    String challenge(UserContext.UserInfo user);

    UserContext.UserInfo verify(String challengeId, String code);

    String method();

    Duration challengeTtl();
}
