package cn.slainc.sign;

import cn.slainc.crypto.asymmetric.RsaKeySize;
import cn.slainc.crypto.asymmetric.RsaUtils;
import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.key.PemUtils;
import cn.slainc.crypto.sm.Sm2Utils;
import cn.slainc.sign.composer.SignComposer;
import cn.slainc.sign.config.SignProperties;
import cn.slainc.sign.key.SignKeyDefinition;
import cn.slainc.sign.key.SignKeyRegistry;
import cn.slainc.sign.store.MemorySignNonceStore;
import cn.slainc.sign.verify.SignVerifier;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

final class SignTestSupport {

    private SignTestSupport() {
    }

    static SignKeyDefinition hmacKey(String appId, cn.slainc.sign.SignAlgorithm algorithm, String secretText) {
        SignKeyDefinition key = new SignKeyDefinition();
        key.setAppId(appId);
        key.setAlgorithm(algorithm.name());
        key.setSecretBase64(Base64Utils.encode(secretText));
        return key;
    }

    static SignKeyDefinition rsaKey(String appId) {
        KeyPair keyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_2048);
        SignKeyDefinition key = new SignKeyDefinition();
        key.setAppId(appId);
        key.setAlgorithm(cn.slainc.sign.SignAlgorithm.RSA_SHA256.name());
        key.setPublicKeyPem(PemUtils.encodePublicKey(keyPair.getPublic()));
        key.setPrivateKeyPem(PemUtils.encodePrivateKey(keyPair.getPrivate()));
        return key;
    }

    static SignKeyDefinition sm2Key(String appId) {
        KeyPair keyPair = Sm2Utils.generateKeyPair();
        SignKeyDefinition key = new SignKeyDefinition();
        key.setAppId(appId);
        key.setAlgorithm(cn.slainc.sign.SignAlgorithm.SM2.name());
        key.setPublicKeyPem(PemUtils.encodePublicKey(keyPair.getPublic()));
        key.setPrivateKeyPem(PemUtils.encodePrivateKey(keyPair.getPrivate()));
        return key;
    }

    static SignProperties properties(SignKeyDefinition... keys) {
        SignProperties properties = new SignProperties();
        List<SignKeyDefinition> list = new ArrayList<>(List.of(keys));
        properties.setKeys(list);
        return properties;
    }

    static SignVerifier localVerifier(SignProperties properties, FakeConfigService configService) {
        SignKeyRegistry registry = new SignKeyRegistry(properties,
                new SingleProvider<>(configService == null ? null : configService));
        return new SignVerifier(registry, new SignComposer(properties), properties, new MemorySignNonceStore());
    }
}
