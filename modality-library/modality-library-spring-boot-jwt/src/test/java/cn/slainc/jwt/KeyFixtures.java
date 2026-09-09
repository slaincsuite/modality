package cn.slainc.jwt;

import cn.slainc.crypto.asymmetric.RsaKeySize;
import cn.slainc.crypto.asymmetric.RsaUtils;
import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.key.PemUtils;
import cn.slainc.crypto.key.SecretKeyUtils;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

public final class KeyFixtures {

    private KeyFixtures() {
    }

    public static String rsaPrivateKeyPem(RsaKeySize keySize) {
        return PemUtils.encodePrivateKey(RsaUtils.generateKeyPair(keySize).getPrivate());
    }

    public static String ecP192PrivateKeyPem() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC",
                    cn.slainc.crypto.CryptoProviders.bouncyCastle());
            generator.initialize(new ECGenParameterSpec("secp192r1"));
            KeyPair keyPair = generator.generateKeyPair();
            return PemUtils.encodePrivateKey(keyPair.getPrivate());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法生成 P-192 测试密钥", exception);
        }
    }

    public static String secretBase64(int length) {
        return Base64Utils.encode(SecretKeyUtils.randomKey(length));
    }
}
