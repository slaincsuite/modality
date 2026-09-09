package cn.slainc.jwt.issue;

import cn.slainc.crypto.jwt.JwtCodec;
import cn.slainc.crypto.jwt.JwtAlgorithm;
import cn.slainc.jwt.algorithm.JwtAlgorithmWhitelist;
import cn.slainc.jwt.config.JwtSettings;
import cn.slainc.jwt.key.JwtKeyEntry;
import cn.slainc.jwt.key.JwtKeyManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class JwtTokenService {

    public static final String CLAIM_TOKEN_TYPE = "token_type";

    private final JwtKeyManager keyManager;

    private final JwtAlgorithmWhitelist whitelist;

    private final JwtSettings settings;

    private final Clock clock;

    public JwtTokenService(JwtKeyManager keyManager, JwtAlgorithmWhitelist whitelist, JwtSettings settings) {
        this(keyManager, whitelist, settings, Clock.systemUTC());
    }

    public JwtTokenService(JwtKeyManager keyManager, JwtAlgorithmWhitelist whitelist, JwtSettings settings, Clock clock) {
        this.keyManager = keyManager;
        this.whitelist = whitelist;
        this.settings = settings;
        this.clock = clock;
    }

    public SignedToken issueToken(TokenIssuanceSpec spec) {
        JwtKeyEntry entry = keyManager.current();
        JwtAlgorithm algorithm = entry.algorithm();
        whitelist.ensureAllowed(algorithm);
        Instant now = clock.instant();
        TokenType tokenType = spec.tokenType() == null ? TokenType.ACCESS : spec.tokenType();
        Duration ttl = settings.ttl(tokenType, spec.ttl());
        Instant expiresAt = spec.expiresAt() != null ? spec.expiresAt() : now.plus(ttl);

        Map<String, Object> claims = new LinkedHashMap<>();
        Map<String, Object> custom = spec.claims();
        if (custom != null) {
            claims.putAll(custom);
        }
        String issuer = settings.issuer();
        if (issuer != null && !issuer.isBlank()) {
            claims.put("iss", issuer);
        }
        claims.put("sub", spec.subject());
        if (!spec.audiences().isEmpty()) {
            claims.put("aud", spec.audiences().size() == 1 ? spec.audiences().get(0) : spec.audiences());
        }
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        long nbfDelay = settings.notBeforeSeconds();
        if (spec.notBeforeDelay() != null) {
            nbfDelay = spec.notBeforeDelay().toSeconds();
        }
        if (nbfDelay > 0) {
            claims.put("nbf", now.plusSeconds(nbfDelay).getEpochSecond());
        }
        String jti = spec.jti() == null || spec.jti().isBlank() ? UUID.randomUUID().toString() : spec.jti();
        claims.put("jti", jti);
        claims.put(CLAIM_TOKEN_TYPE, tokenType.name());
        if (tokenType == TokenType.ID_TOKEN) {
            applyOidcClaims(claims, spec);
        }

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("typ", "JWT");
        header.put("kid", entry.kid());
        String tokenValue = JwtCodec.encode(algorithm, entry.signingKey(), header, claims);
        return new SignedToken(tokenValue, jti, tokenType, expiresAt, entry.kid());
    }

    public List<SignedToken> issueBatch(List<TokenIssuanceSpec> specs) {
        List<SignedToken> tokens = new ArrayList<>(specs == null ? 0 : specs.size());
        if (specs == null) {
            return tokens;
        }
        for (TokenIssuanceSpec spec : specs) {
            tokens.add(issueToken(spec));
        }
        return tokens;
    }

    private void applyOidcClaims(Map<String, Object> claims, TokenIssuanceSpec spec) {
        if (spec.nonce() != null && !spec.nonce().isBlank()) {
            claims.put("nonce", spec.nonce());
        }
        if (spec.authTime() != null) {
            claims.put("auth_time", spec.authTime());
        }
        if (spec.acr() != null && !spec.acr().isBlank()) {
            claims.put("acr", spec.acr());
        }
        if (!spec.amr().isEmpty()) {
            claims.put("amr", spec.amr());
        }
        if (spec.azp() != null && !spec.azp().isBlank()) {
            claims.put("azp", spec.azp());
        }
    }
}
