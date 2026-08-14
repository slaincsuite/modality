package cn.slainc.crypto;

import java.security.Provider;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public final class CryptoProviders {

    public static final String BOUNCY_CASTLE = "BC";

    private static final class Holder {

        private static final Provider PROVIDER = register();
    }

    private CryptoProviders() {
    }

    public static Provider bouncyCastle() {
        return Holder.PROVIDER;
    }

    private static Provider register() {
        Provider existing = Security.getProvider(BOUNCY_CASTLE);
        if (existing != null) {
            return existing;
        }
        Provider provider = new BouncyCastleProvider();
        Security.addProvider(provider);
        return provider;
    }
}
