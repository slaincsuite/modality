package cn.slainc.changeaudit.event;

public interface AfterCommitExecutor {

    void afterCommit(Runnable action);
}
