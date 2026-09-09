package cn.slainc.jwt.issue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TokenIssuanceSpec {

    private final String subject;

    private final TokenType tokenType;

    private final List<String> audiences;

    private final Map<String, Object> claims;

    private final String jti;

    private final String nonce;

    private final Long authTime;

    private final String acr;

    private final List<String> amr;

    private final String azp;

    private final Duration ttl;

    private final Instant expiresAt;

    private final Duration notBeforeDelay;

    private TokenIssuanceSpec(Builder builder) {
        this.subject = builder.subject;
        this.tokenType = builder.tokenType;
        this.audiences = List.copyOf(builder.audiences);
        this.claims = Map.copyOf(builder.claims);
        this.jti = builder.jti;
        this.nonce = builder.nonce;
        this.authTime = builder.authTime;
        this.acr = builder.acr;
        this.amr = List.copyOf(builder.amr);
        this.azp = builder.azp;
        this.ttl = builder.ttl;
        this.expiresAt = builder.expiresAt;
        this.notBeforeDelay = builder.notBeforeDelay;
    }

    public static Builder builder(String subject) {
        return new Builder(subject);
    }

    public static TokenIssuanceSpec of(String subject) {
        return builder(subject).build();
    }

    public String subject() {
        return subject;
    }

    public TokenType tokenType() {
        return tokenType;
    }

    public List<String> audiences() {
        return audiences;
    }

    public Map<String, Object> claims() {
        return claims;
    }

    public String jti() {
        return jti;
    }

    public String nonce() {
        return nonce;
    }

    public Long authTime() {
        return authTime;
    }

    public String acr() {
        return acr;
    }

    public List<String> amr() {
        return amr;
    }

    public String azp() {
        return azp;
    }

    public Duration ttl() {
        return ttl;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Duration notBeforeDelay() {
        return notBeforeDelay;
    }

    public static class Builder {

        private final String subject;

        private TokenType tokenType = TokenType.ACCESS;

        private final List<String> audiences = new ArrayList<>();

        private final Map<String, Object> claims = new LinkedHashMap<>();

        private String jti;

        private String nonce;

        private Long authTime;

        private String acr;

        private final List<String> amr = new ArrayList<>();

        private String azp;

        private Duration ttl;

        private Instant expiresAt;

        private Duration notBeforeDelay;

        private Builder(String subject) {
            if (subject == null || subject.isBlank()) {
                throw new IllegalArgumentException("签发令牌必须指定 subject（登录主体）");
            }
            this.subject = subject;
        }

        public Builder tokenType(TokenType tokenType) {
            if (tokenType == null) {
                throw new IllegalArgumentException("令牌类型不能为空");
            }
            this.tokenType = tokenType;
            return this;
        }

        public Builder audience(String audience) {
            if (audience != null && !audience.isBlank()) {
                audiences.add(audience);
            }
            return this;
        }

        public Builder audiences(List<String> audiences) {
            if (audiences != null) {
                for (String audience : audiences) {
                    audience(audience);
                }
            }
            return this;
        }

        public Builder claim(String name, Object value) {
            if (name != null && !name.isBlank()) {
                claims.put(name, value);
            }
            return this;
        }

        public Builder claims(Map<String, Object> claims) {
            if (claims != null) {
                claims.forEach((name, value) -> {
                    if (name != null && !name.isBlank()) {
                        this.claims.put(name, value);
                    }
                });
            }
            return this;
        }

        public Builder jti(String jti) {
            this.jti = jti;
            return this;
        }

        public Builder nonce(String nonce) {
            this.nonce = nonce;
            return this;
        }

        public Builder authTime(Long epochSecond) {
            this.authTime = epochSecond;
            return this;
        }

        public Builder acr(String acr) {
            this.acr = acr;
            return this;
        }

        public Builder amr(List<String> amr) {
            if (amr != null) {
                this.amr.addAll(amr);
            }
            return this;
        }

        public Builder azp(String azp) {
            this.azp = azp;
            return this;
        }

        public Builder ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder notBeforeDelay(Duration notBeforeDelay) {
            this.notBeforeDelay = notBeforeDelay;
            return this;
        }

        public TokenIssuanceSpec build() {
            return new TokenIssuanceSpec(this);
        }
    }
}
