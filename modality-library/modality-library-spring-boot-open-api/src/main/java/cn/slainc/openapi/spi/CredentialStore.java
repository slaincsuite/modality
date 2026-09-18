package cn.slainc.openapi.spi;

import cn.slainc.openapi.model.AppCredential;

import java.util.Optional;

public interface CredentialStore {

    AppCredential save(AppCredential credential);

    Optional<AppCredential> find(String appId);

    void delete(String appId);
}
