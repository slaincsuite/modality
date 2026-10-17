package cn.slainc.modules.auth.dto;

public record MfaPolicyRequest(Boolean loginProtect, Boolean operationProtect) {
}
