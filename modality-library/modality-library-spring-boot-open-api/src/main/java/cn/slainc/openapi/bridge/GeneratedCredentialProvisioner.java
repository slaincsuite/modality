package cn.slainc.openapi.bridge;

import cn.slainc.openapi.model.App;
import cn.slainc.openapi.model.AppCredential;
import cn.slainc.openapi.spi.CredentialProvisioner;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public class GeneratedCredentialProvisioner implements CredentialProvisioner {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public int priority() {
        return Integer.MIN_VALUE;
    }

    @Override
    public boolean supports(App app) {
        return true;
    }

    @Override
    public AppCredential issue(App app) {
        byte[] idBytes = new byte[8];
        RANDOM.nextBytes(idBytes);
        AppCredential credential = new AppCredential();
        credential.setClientId("cli_" + HexFormat.of().formatHex(idBytes));
        credential.setClientSecret(randomSecret());
        credential.setType(AppCredential.Type.GENERATED);
        return credential;
    }

    @Override
    public AppCredential rotate(AppCredential existing, App app, long graceMillis) {
        AppCredential rotated = new AppCredential();
        rotated.setClientId(existing.getClientId());
        rotated.setClientSecret(randomSecret());
        rotated.setType(existing.getType());
        rotated.setPreviousSecretExpireAt(graceMillis <= 0 ? null
                : System.currentTimeMillis() + graceMillis);
        return rotated;
    }

    @Override
    public void changeStatus(String clientId, boolean enabled) {
    }

    public static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
