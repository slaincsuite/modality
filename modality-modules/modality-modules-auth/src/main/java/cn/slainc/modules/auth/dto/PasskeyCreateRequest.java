package cn.slainc.modules.auth.dto;

public record PasskeyCreateRequest(String credentialId, String name, String publicKey) {
}
