package cn.slainc.excel.security;

public final class SecurityOptions {

    private String openPassword;
    private String editPassword;
    private boolean readOnlyRecommended;
    private boolean lockStructure = true;

    public static SecurityOptions defaults() {
        return new SecurityOptions();
    }

    public static SecurityOptions ofOpenPassword(String password) {
        SecurityOptions options = new SecurityOptions();
        options.openPassword = password;
        return options;
    }

    public String getOpenPassword() {
        return openPassword;
    }

    public SecurityOptions openPassword(String value) {
        this.openPassword = value;
        return this;
    }

    public String getEditPassword() {
        return editPassword;
    }

    public SecurityOptions editPassword(String value) {
        this.editPassword = value;
        return this;
    }

    public boolean isReadOnlyRecommended() {
        return readOnlyRecommended;
    }

    public SecurityOptions readOnlyRecommended(boolean value) {
        this.readOnlyRecommended = value;
        return this;
    }

    public boolean isLockStructure() {
        return lockStructure;
    }

    public SecurityOptions lockStructure(boolean value) {
        this.lockStructure = value;
        return this;
    }

    public boolean isEmpty() {
        return blank(openPassword) && blank(editPassword) && !readOnlyRecommended;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
