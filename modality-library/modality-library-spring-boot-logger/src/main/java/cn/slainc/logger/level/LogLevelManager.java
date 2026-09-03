package cn.slainc.logger.level;

public interface LogLevelManager {

    boolean setLevel(String loggerName, String level);

    String getLevel(String loggerName);

    int refresh();
}
