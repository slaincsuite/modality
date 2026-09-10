package cn.slainc.jwt.introspect;

import cn.slainc.jwt.verify.JwtVerification;
import cn.slainc.jwt.verify.JwtVerifier;

import java.util.Map;

public class IntrospectionService {

    private final JwtVerifier verifier;

    public IntrospectionService(JwtVerifier verifier) {
        this.verifier = verifier;
    }

    public IntrospectionResult introspect(String token) {
        JwtVerification verification = verifier.verify(token);
        if (!verification.valid()) {
            return IntrospectionResult.inactive();
        }
        Map<String, Object> claims = verification.claims();
        Object exp = claims.get("exp");
        Long expSeconds = exp instanceof Number number ? number.longValue() : null;
        return new IntrospectionResult(true, claims,
                verification.tokenType() == null ? null : verification.tokenType().name(), expSeconds);
    }
}
