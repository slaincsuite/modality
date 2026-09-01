package cn.slainc.storage.antivirus;

public record VirusScanResult(Status status, String signature, String detail) {

    public enum Status {

        CLEAN,
        INFECTED,
        UNAVAILABLE
    }

    public static VirusScanResult clean(String detail) {
        return new VirusScanResult(Status.CLEAN, null, detail);
    }

    public static VirusScanResult infected(String signature, String detail) {
        return new VirusScanResult(Status.INFECTED, signature, detail);
    }

    public static VirusScanResult unavailable(String detail) {
        return new VirusScanResult(Status.UNAVAILABLE, null, detail);
    }

    public boolean clean() {
        return status == Status.CLEAN;
    }

    public boolean infected() {
        return status == Status.INFECTED;
    }

    public boolean unavailable() {
        return status == Status.UNAVAILABLE;
    }
}
