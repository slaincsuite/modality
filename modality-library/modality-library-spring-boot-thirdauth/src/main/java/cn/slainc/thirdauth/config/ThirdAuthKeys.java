package cn.slainc.thirdauth.config;

public final class ThirdAuthKeys {

    public static final String PREFIX = "modality:thirdauth:";

    public static String stateKey(String state) {
        return PREFIX + "state:" + state;
    }

    public static String qrTicketKey(String ticketId) {
        return PREFIX + "qr:" + ticketId;
    }

    public static String platformConfigKey(String platformCode, String field) {
        return "modality.thirdauth.platform." + platformCode + "." + field;
    }

    private ThirdAuthKeys() {
    }
}
