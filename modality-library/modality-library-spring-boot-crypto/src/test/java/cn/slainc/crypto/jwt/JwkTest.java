package cn.slainc.crypto.jwt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.asymmetric.EcCurve;
import cn.slainc.crypto.asymmetric.EcdsaUtils;
import cn.slainc.crypto.asymmetric.EdDsaAlgorithm;
import cn.slainc.crypto.asymmetric.EdDsaUtils;
import cn.slainc.crypto.asymmetric.RsaKeySize;
import cn.slainc.crypto.asymmetric.RsaUtils;
import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.random.SecureRandomUtils;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class JwkTest {

    @Test
    void roundTripsRsaKeys() {
        KeyPair keyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_2048);
        Jwk jwk = Jwk.fromPublicKey(keyPair.getPublic());
        assertEquals("RSA", jwk.kty());
        assertFalse(jwk.isPrivate());
        assertNull(jwk.kid());
        PublicKey restored = jwk.toPublicKey();
        assertEquals(((RSAPublicKey) keyPair.getPublic()).getModulus(), ((RSAPublicKey) restored).getModulus());
        assertArrayEquals(keyPair.getPublic().getEncoded(), restored.getEncoded());
        Jwk privateJwk = Jwk.fromPrivateKey(keyPair.getPrivate());
        assertTrue(privateJwk.isPrivate());
        assertEquals(keyPair.getPrivate(), privateJwk.toPrivateKey());
    }

    @Test
    void roundTripsEcKeys() {
        for (EcCurve curve : EcCurve.values()) {
            KeyPair keyPair = EcdsaUtils.generateKeyPair(curve);
            Jwk jwk = Jwk.fromPublicKey(keyPair.getPublic());
            assertEquals("EC", jwk.kty());
            assertEquals(curveName(curve), jwk.crv());
            assertEquals(jwk.toMap(), Jwk.fromPublicKey(jwk.toPublicKey()).toMap());
            assertEquals(Jwk.fromPrivateKey(keyPair.getPrivate()).toMap(), Jwk.fromPrivateKey(Jwk.fromPrivateKey(keyPair.getPrivate()).toPrivateKey()).toMap());
        }
    }

    @Test
    void roundTripsEdwardsKeys() {
        for (EdDsaAlgorithm algorithm : EdDsaAlgorithm.values()) {
            KeyPair keyPair = EdDsaUtils.generateKeyPair(algorithm);
            Jwk jwk = Jwk.fromPublicKey(keyPair.getPublic());
            assertEquals("OKP", jwk.kty());
            assertEquals(algorithm.jcaName(), jwk.crv());
            assertEquals(algorithm.keyLength(), Base64Utils.decodeUrl(String.valueOf(jwk.toMap().get("x"))).length);
            assertEquals(jwk.toMap(), Jwk.fromPublicKey(jwk.toPublicKey()).toMap());
            assertEquals(Jwk.fromPrivateKey(keyPair.getPrivate()).toMap(), Jwk.fromPrivateKey(Jwk.fromPrivateKey(keyPair.getPrivate()).toPrivateKey()).toMap());
        }
    }

    @Test
    void handlesSymmetricKeysAndMetadata() {
        SecretKey key = new SecretKeySpec(SecureRandomUtils.nextBytes(32), "HmacSHA256");
        Jwk jwk = Jwk.fromSecretKey(key, "HS256");
        assertEquals("oct", jwk.kty());
        assertEquals("HS256", jwk.alg());
        assertEquals(key, jwk.toSecretKey("HmacSHA256"));
        assertTrue(jwk.isPrivate());
        Jwk described = jwk.withKid("key-1").withUse("sig");
        assertEquals("key-1", described.kid());
        assertEquals("sig", described.use());
        assertEquals("HS256", described.alg());
        assertNull(described.withKid(null).kid());
        assertThrows(CryptoException.class, () -> jwk.toPublicKey());
        assertThrows(CryptoException.class, () -> jwk.toPrivateKey());
    }

    @Test
    void serializesToJsonAndBack() {
        KeyPair keyPair = RsaUtils.generateKeyPair(RsaKeySize.RSA_1024);
        Jwk jwk = Jwk.fromPublicKey(keyPair.getPublic()).withKid("rsa-1").withAlg("RS256").withUse("sig");
        String json = jwk.toJson();
        Jwk parsed = Jwk.fromJson(json);
        assertEquals(jwk.toMap(), parsed.toMap());
        assertEquals("rsa-1", parsed.kid());
        assertArrayEquals(keyPair.getPublic().getEncoded(), parsed.toPublicKey().getEncoded());
        Jwk stripped = Jwk.publicOnly(Jwk.fromPrivateKey(keyPair.getPrivate()).withKid("rsa-1"));
        assertFalse(stripped.isPrivate());
        assertNull(stripped.toMap().get("d"));
        assertTrue(stripped.toMap().containsKey("n"));
        assertThrows(CryptoException.class, () -> Jwk.fromJson("{}"));
        assertThrows(CryptoException.class, () -> Jwk.fromJson("[1]"));
    }

    @Test
    void readsRfc7515SymmetricKey() {
        Jwk jwk = Jwk.fromJson("{\"kty\":\"oct\",\"k\":\"AyM1SysPpbyDfgZld3umj1qzKObwVMkoqQ-EstJQLr_T-1qS0gZH75aKtMN3Yj0iPS4hcgUuTwjAzZr1Z9CAow\"}");
        assertEquals("oct", jwk.kty());
        assertEquals(64, jwk.toSecretKey("HmacSHA256").getEncoded().length);
    }

    @Test
    void managesJwkSets() {
        KeyPair first = RsaUtils.generateKeyPair(RsaKeySize.RSA_1024);
        KeyPair second = EcdsaUtils.generateKeyPair(EcCurve.P_256);
        JwkSet set = JwkSet.of(Jwk.fromPublicKey(first.getPublic()).withKid("rsa-1"), Jwk.fromPrivateKey(second.getPrivate()).withKid("ec-1"));
        assertEquals(2, set.size());
        assertFalse(set.isEmpty());
        assertEquals("rsa-1", set.first().kid());
        assertEquals("ec-1", set.findByKid("ec-1").kid());
        assertNull(set.findByKid("missing"));
        assertEquals("rsa-1", set.findByKidOrFirst("missing").kid());
        assertEquals("ec-1", set.requireByKid("ec-1").kid());
        assertThrows(CryptoException.class, () -> set.requireByKid("missing"));
        JwkSet parsed = JwkSet.parse(set.toJson());
        assertEquals(2, parsed.size());
        assertTrue(parsed.findByKid("ec-1").isPrivate());
        assertFalse(set.publicKeys().findByKid("ec-1").isPrivate());
        assertEquals(3, set.add(Jwk.fromPublicKey(first.getPublic()).withKid("rsa-2")).size());
        assertTrue(new JwkSet(null).isEmpty());
        assertThrows(CryptoException.class, () -> new JwkSet(null).first());
        assertThrows(CryptoException.class, () -> JwkSet.parse("{\"other\":[]}"));
        assertThrows(CryptoException.class, () -> JwkSet.parse("{\"keys\":[1]}"));
    }

    private static String curveName(EcCurve curve) {
        return switch (curve) {
            case P_256 -> "P-256";
            case P_384 -> "P-384";
            case P_521 -> "P-521";
        };
    }
}
