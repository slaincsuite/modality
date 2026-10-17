package cn.slainc.modules.upms.dto;

public record UserToggleRequest(Boolean enabled, Boolean value, Boolean consoleAccess, Boolean mustChangePassword) {

    public Boolean resolve() {
        if (enabled != null) {
            return enabled;
        }
        if (value != null) {
            return value;
        }
        if (consoleAccess != null) {
            return consoleAccess;
        }
        return mustChangePassword;
    }
}
