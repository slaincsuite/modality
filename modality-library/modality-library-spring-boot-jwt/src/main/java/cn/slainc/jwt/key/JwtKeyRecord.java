package cn.slainc.jwt.key;

import java.time.Instant;

public record JwtKeyRecord(String kid, String algorithm, String pem, Instant createdAt) {
}
