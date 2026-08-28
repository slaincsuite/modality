package cn.slainc.idempotent.support;

@FunctionalInterface
public interface IdempotentInvocation {

    Object proceed() throws Throwable;
}
