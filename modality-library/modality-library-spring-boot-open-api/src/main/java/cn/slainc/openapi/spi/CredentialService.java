package cn.slainc.openapi.spi;

import cn.slainc.openapi.domain.AppCredential;

public interface CredentialService {

    AppCredential issue(String appId);

    AppCredential rotate(String appId, long graceMillis);

    AppCredential enable(String appId);

    AppCredential disable(String appId);

    AppCredential find(String appId);
}
