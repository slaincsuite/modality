package cn.slainc.changeaudit.spi;

public interface ChangeRecorder {

    void record(ChangeRecord change);
}
