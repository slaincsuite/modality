package cn.slainc.thirdauth.config;

import cn.slainc.thirdauth.ThirdPartyPlatform;

import java.util.Optional;

public interface ThirdAuthConfigProvider {

    Optional<String> find(ThirdPartyPlatform platform, String field);
}
