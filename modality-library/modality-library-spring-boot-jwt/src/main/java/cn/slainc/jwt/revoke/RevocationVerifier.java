package cn.slainc.jwt.revoke;

@FunctionalInterface
public interface RevocationVerifier {

    void requireValid(String token);
}
