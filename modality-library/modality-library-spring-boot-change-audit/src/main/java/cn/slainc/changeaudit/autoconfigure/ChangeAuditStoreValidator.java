package cn.slainc.changeaudit.autoconfigure;

import org.springframework.beans.factory.InitializingBean;

public class ChangeAuditStoreValidator implements InitializingBean {

    static final String STORE_JDBC = "jdbc";

    static final String STORE_NOOP = "noop";

    private final String store;

    public ChangeAuditStoreValidator(String store) {
        this.store = store;
    }

    // 取值写错时装配条件不成立会静默退化成空实现，审计一条不落地，这里直接启动失败
    @Override
    public void afterPropertiesSet() {
        if (!STORE_JDBC.equals(store) && !STORE_NOOP.equals(store)) {
            throw new IllegalStateException("不支持的变更审计存储类型：" + store + "，可选 " + STORE_JDBC + " / "
                    + STORE_NOOP);
        }
    }
}
