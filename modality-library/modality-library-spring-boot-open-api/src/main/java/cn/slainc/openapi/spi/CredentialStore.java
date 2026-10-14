package cn.slainc.openapi.spi;

import cn.slainc.openapi.domain.AppCredential;

import java.util.Optional;

public interface CredentialStore {

    AppCredential save(AppCredential credential);

    Optional<AppCredential> find(String appId);

    void delete(String appId);
}
