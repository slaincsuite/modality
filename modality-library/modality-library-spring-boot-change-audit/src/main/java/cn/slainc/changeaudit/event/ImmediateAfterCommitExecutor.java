package cn.slainc.changeaudit.event;

public class ImmediateAfterCommitExecutor implements AfterCommitExecutor {

    @Override
    public void afterCommit(Runnable action) {
        action.run();
    }
}
