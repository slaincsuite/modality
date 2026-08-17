package cn.slainc.crypto.key;

import cn.slainc.crypto.CryptoException;
import java.util.Set;
import javax.crypto.SecretKey;

public interface CryptoKeyProvider {

    SecretKey key(String keyId);

    String currentKeyId();

    default SecretKey currentKey() {
        return key(currentKeyId());
    }

    default byte[] currentKeyBytes() {
        return currentKey().getEncoded();
    }

    default boolean contains(String keyId) {
        try {
            return key(keyId) != null;
        } catch (CryptoException exception) {
            return false;
        }
    }

    default Set<String> keyIds() {
        return Set.of(currentKeyId());
    }
}
