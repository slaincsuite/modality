package cn.slainc.jwt.key;

import cn.slainc.crypto.jwt.Jwk;
import cn.slainc.crypto.jwt.JwtAlgorithm;

import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import javax.crypto.SecretKey;

public final class JwtKeyEntry {

    private final String kid;

    private final JwtAlgorithm algorithm;

    private final Key signingKey;

    private final PublicKey publicKey;

    private final Instant createdAt;

    JwtKeyEntry(String kid, JwtAlgorithm algorithm, Key signingKey, PublicKey publicKey, Instant createdAt) {
        this.kid = kid;
        this.algorithm = algorithm;
        this.signingKey = signingKey;
        this.publicKey = publicKey;
        this.createdAt = createdAt;
    }

    public String kid() {
        return kid;
    }

    public JwtAlgorithm algorithm() {
        return algorithm;
    }

    public Key signingKey() {
        return signingKey;
    }

    public Key verifyKey() {
        return publicKey == null ? signingKey : publicKey;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean isSymmetric() {
        return signingKey instanceof SecretKey;
    }

    public PublicKey publicKey() {
        return publicKey;
    }

    public Jwk publicJwk() {
        if (publicKey == null) {
            throw new IllegalStateException("密钥 " + kid + " 没有可导出的公钥");
        }
        return Jwk.fromPublicKey(publicKey).withKid(kid).withUse("sig").withAlg(algorithm.alg());
    }
}
