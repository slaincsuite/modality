package cn.slainc.session.config;

public final class SessionKeys {

    public static final String PREFIX = "modality:session:";

    private SessionKeys() {
    }

    public static String kickNoticeTopic() {
        return PREFIX + "kick:notice";
    }

    public static String cleanupReportKey() {
        return PREFIX + "cleanup:last";
    }
}
