package cn.slainc.jwt.algorithm;

import cn.slainc.crypto.jwt.JwtAlgorithm;
import cn.slainc.jwt.verify.JwtVerifyException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class JwtAlgorithmWhitelist {

    private final Set<JwtAlgorithm> allowed;

    public JwtAlgorithmWhitelist(List<JwtAlgorithm> allowedAlgorithms) {
        if (allowedAlgorithms == null || allowedAlgorithms.isEmpty()) {
            throw new JwtVerifyException("算法白名单不能为空，至少需要配置一个允许的签名算法");
        }
        this.allowed = new LinkedHashSet<>(allowedAlgorithms);
    }

    public static JwtAlgorithmWhitelist defaults() {
        return new JwtAlgorithmWhitelist(List.of(JwtAlgorithm.HS256, JwtAlgorithm.RS256, JwtAlgorithm.ES256));
    }

    public boolean isAllowed(JwtAlgorithm algorithm) {
        return algorithm != null && allowed.contains(algorithm);
    }

    public Set<JwtAlgorithm> allowedAlgorithms() {
        return Set.copyOf(allowed);
    }

    public void ensureAllowed(JwtAlgorithm algorithm) {
        if (algorithm == null) {
            throw new JwtVerifyException("JWT 签名算法不能为空");
        }
        if (!isAllowed(algorithm)) {
            throw new JwtVerifyException("签名算法 " + algorithm.alg() + " 不在允许的白名单内，已拒绝验签");
        }
    }
}
