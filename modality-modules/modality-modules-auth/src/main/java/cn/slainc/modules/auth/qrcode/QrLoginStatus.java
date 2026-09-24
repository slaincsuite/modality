package cn.slainc.modules.auth.qrcode;

public enum QrLoginStatus {

    WAITING("待扫码"),

    SCANNED("已扫码，待确认"),

    CONFIRMED("已确认，待交换会话"),

    CANCELLED("已取消"),

    EXPIRED("已过期");

    private final String label;

    QrLoginStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
