package cn.slainc.changeaudit.spi;

public interface SnapshotLoader {

    String type();

    Object load(String id);
}
