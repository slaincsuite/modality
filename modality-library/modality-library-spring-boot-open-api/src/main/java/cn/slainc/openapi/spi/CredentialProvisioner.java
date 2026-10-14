package cn.slainc.openapi.spi;

import cn.slainc.openapi.domain.App;
import cn.slainc.openapi.domain.AppCredential;

public interface CredentialProvisioner {

    default int priority() {
        return 0;
    }

    boolean supports(App app);

    AppCredential issue(App app);

    AppCredential rotate(AppCredential existing, App app, long graceMillis);

    void changeStatus(String clientId, boolean enabled);
}
