package cn.slainc.crypto.jwt;

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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class JwtCodecTest {

    private static final String RFC_HEADER = "eyJ0eXAiOiJKV1QiLA0KICJhbGciOiJIUzI1NiJ9";

    private static final String RFC_PAYLOAD = "eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQogImh0dHA6Ly9leGFtcGxlLmNvbS9pc19yb290Ijp0cnVlfQ";

    private static final String RFC_SIGNATURE = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

    private static final String RFC_KEY = "AyM1SysPpbyDfgZld3umj1qzKObwVMkoqQ-EstJQLr_T-1qS0gZH75aKtMN3Yj0iPS4hcgUuTwjAzZr1Z9CAow";

    @Test
    void verifiesRfc7515HmacVector() {
        String token = RFC_HEADER + "." + RFC_PAYLOAD + "." + RFC_SIGNATURE;
        SecretKey key = new SecretKeySpec(Base64Utils.decodeUrl(RFC_KEY), "HmacSHA256");
        assertTrue(JwtCodec.verify(token, key, JwtAlgorithm.HS256));
        Jwt jwt = JwtCodec.decode(token, key);
        assertEquals("HS256", jwt.algorithm());
        assertEquals("JWT", jwt.type());
        assertNull(jwt.subject());
        assertEquals("joe", jwt.issuer());
        assertEquals(1300819380L, jwt.expiresAt().getEpochSecond());
        assertEquals(Boolean.TRUE, jwt.claim("http://example.com/is_root"));
        assertEquals(RFC_HEADER + "." + RFC_PAYLOAD, jwt.signingInput());
        assertEquals(RFC_SIGNATURE, Base64Utils.encodeUrlNoPadding(jwt.signature()));
        SecretKey other = new SecretKeySpec(SecureRandomUtils.nextBytes(64), "HmacSHA256");
        assertFalse(JwtCodec.verify(token, other));
    }

    @Test
    void encodesAndDecodesHmacTokens() {
        for (JwtAlgorithm algorithm : List.of(JwtAlgorithm.HS256, JwtAlgorithm.HS384, JwtAlgorithm.HS512)) {
            SecretKey key = new SecretKeySpec(SecureRandomUtils.nextBytes(32), algorithm.jcaName());
            String token = JwtCodec.encode(algorithm, key, claims());
            assertEquals(3, token.split("\\.").length);
            Jwt jwt = JwtCodec.decode(token, key, algorithm);
            assertEquals(algorithm.alg(), jwt.algorithm());
            assertEquals("modality", jwt.subject());
            assertTrue(jwt.isActive());
            assertFalse(jwt.isExpired());
        }
    }

    @Test
    void encodesAndDecodesAsymmetricTokens() {
        assertToken(JwtAlgorithm.RS256, RsaUtils.generateKeyPair(RsaKeySize.RSA_2048));
        assertToken(JwtAlgorithm.RS384, RsaUtils.generateKeyPair(RsaKeySize.RSA_2048));
        assertToken(JwtAlgorithm.RS512, RsaUtils.generateKeyPair(RsaKeySize.RSA_2048));
        assertToken(JwtAlgorithm.PS256, RsaUtils.generateKeyPair(RsaKeySize.RSA_2048));
        assertToken(JwtAlgorithm.PS384, RsaUtils.generateKeyPair(RsaKeySize.RSA_2048));
        assertToken(JwtAlgorithm.PS512, RsaUtils.generateKeyPair(RsaKeySize.RSA_2048));
        assertToken(JwtAlgorithm.ES256, EcdsaUtils.generateKeyPair(EcCurve.P_256));
        assertToken(JwtAlgorithm.ES384, EcdsaUtils.generateKeyPair(EcCurve.P_384));
        assertToken(JwtAlgorithm.ES512, EcdsaUtils.generateKeyPair(EcCurve.P_521));
        assertToken(JwtAlgorithm.EdDSA, EdDsaUtils.generateKeyPair(EdDsaAlgorithm.ED25519));
    }

    @Test
    void rejectsTamperedAndMismatchedTokens() {
        SecretKey key = new SecretKeySpec(SecureRandomUtils.nextBytes(32), "HmacSHA256");
        String token = JwtCodec.encode(JwtAlgorithm.HS256, key, claims());
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "B" : "A");
        assertFalse(JwtCodec.verify(tampered, key));
        assertThrows(CryptoException.class, () -> JwtCodec.decode(tampered, key));
        assertThrows(CryptoException.class, () -> JwtCodec.decode(token, key, JwtAlgorithm.HS384));
        assertThrows(CryptoException.class, () -> JwtCodec.decode(token, key, JwtAlgorithm.RS256));
        assertThrows(CryptoException.class, () -> JwtCodec.decode("not-a-token", key));
        assertThrows(CryptoException.class, () -> JwtCodec.encode(JwtAlgorithm.HS256, null, claims()));
        assertThrows(CryptoException.class, () -> JwtCodec.encode(JwtAlgorithm.HS256, key, null));
        assertThrows(CryptoException.class, () -> JwtAlgorithm.fromAlg("XX999"));
        KeyPair rsa = RsaUtils.generateKeyPair(RsaKeySize.RSA_1024);
        assertThrows(CryptoException.class, () -> JwtCodec.encode(JwtAlgorithm.RS256, key, claims()));
        assertThrows(CryptoException.class, () -> JwtCodec.decode(JwtCodec.encode(JwtAlgorithm.ES256, EcdsaUtils.generateKeyPair(EcCurve.P_384).getPrivate(), claims()), rsa.getPublic()));
    }

    @Test
    void readsClaimsAndTimeWindows() {
        SecretKey key = new SecretKeySpec(SecureRandomUtils.nextBytes(32), "HmacSHA256");
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("kid", "key-1");
        headers.put("custom", "value");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "modality");
        claims.put("aud", List.of("api", "web"));
        claims.put("iat", Instant.now().minusSeconds(60).getEpochSecond());
        claims.put("nbf", Instant.now().minusSeconds(30).getEpochSecond());
        claims.put("exp", Instant.now().plusSeconds(600).getEpochSecond());
        claims.put("jti", "token-1");
        claims.put("admin", true);
        claims.put("count", 7);
        String token = JwtCodec.encode(JwtAlgorithm.HS256, key, headers, claims);
        Jwt jwt = JwtCodec.decodeUnverified(token);
        assertEquals("key-1", jwt.keyId());
        assertEquals("value", jwt.header().get("custom"));
        assertEquals("api", jwt.audience());
        assertEquals("token-1", jwt.jwtId());
        assertEquals(7, jwt.intClaim("count"));
        assertEquals(Boolean.TRUE, jwt.booleanClaim("admin"));
        assertTrue(jwt.hasClaim("exp"));
        assertNull(jwt.claim("missing"));
        assertNull(jwt.stringClaim("missing"));
        assertNull(jwt.longClaim("missing"));
        assertNull(jwt.booleanClaim("missing"));
        assertTrue(jwt.isActive());
        assertFalse(jwt.isExpired());
        assertTrue(jwt.issuedAt().isBefore(Instant.now()));
        assertTrue(jwt.notBefore().isBefore(Instant.now()));
        assertEquals("HS256", JwtCodec.algorithm(token));
        assertTrue(JwtCodec.verify(token, key));
        assertNull(JwtCodec.decodeUnverified(JwtCodec.encode(JwtAlgorithm.HS256, key, Map.of("sub", "x"))).expiresAt());
    }

    @Test
    void detectsExpiredTokens() {
        SecretKey key = new SecretKeySpec(SecureRandomUtils.nextBytes(32), "HmacSHA256");
        Map<String, Object> expired = new LinkedHashMap<>();
        expired.put("sub", "modality");
        expired.put("exp", Instant.now().minusSeconds(60).getEpochSecond());
        expired.put("nbf", Instant.now().plusSeconds(600).getEpochSecond());
        Jwt jwt = JwtCodec.decode(JwtCodec.encode(JwtAlgorithm.HS256, key, expired), key);
        assertTrue(jwt.isExpired());
        assertFalse(jwt.isActive());
        assertEquals("single", JwtCodec.decodeUnverified(JwtCodec.encode(JwtAlgorithm.HS256, key, Map.of("aud", "single"))).audience());
    }

    @Test
    void exposesAlgorithmMetadata() {
        assertEquals(JwtKeyType.MAC, JwtAlgorithm.HS256.keyType());
        assertEquals("RSASSA-PSS", JwtAlgorithm.PS256.jcaName());
        assertEquals(32, JwtAlgorithm.PS256.saltLength());
        assertTrue(JwtAlgorithm.PS256.isPss());
        assertTrue(JwtAlgorithm.ES512.isEc());
        assertEquals(521, JwtAlgorithm.ES512.fieldSize());
        assertTrue(JwtAlgorithm.EdDSA.isOkp());
        assertTrue(JwtAlgorithm.RS256.isRsa());
        assertTrue(JwtAlgorithm.HS256.isHmac());
        assertEquals(JwtAlgorithm.HS256, JwtAlgorithm.forKey(new SecretKeySpec(new byte[32], "HmacSHA256")));
        assertEquals(JwtAlgorithm.ES256, JwtAlgorithm.forKey(EcdsaUtils.generateKeyPair(EcCurve.P_256).getPublic()));
    }

    private static void assertToken(JwtAlgorithm algorithm, KeyPair keyPair) {
        String token = JwtCodec.encode(algorithm, keyPair.getPrivate(), claims());
        assertTrue(JwtCodec.verify(token, keyPair.getPublic(), algorithm));
        Jwt jwt = JwtCodec.decode(token, keyPair.getPublic());
        assertEquals(algorithm.alg(), jwt.algorithm());
        assertEquals("modality", jwt.claim("sub"));
    }

    private static Map<String, Object> claims() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "modality");
        claims.put("iss", "modality-platform");
        claims.put("exp", Instant.now().plusSeconds(300).getEpochSecond());
        return claims;
    }
}
