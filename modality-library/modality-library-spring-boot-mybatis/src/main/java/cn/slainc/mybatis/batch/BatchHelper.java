package cn.slainc.mybatis.batch;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionHolder;
import org.mybatis.spring.SqlSessionUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.function.BiConsumer;

public class BatchHelper {

    public static final int DEFAULT_BATCH_SIZE = 500;

    private final SqlSessionFactory sqlSessionFactory;

    public BatchHelper(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    public <T> int saveBatch(Class<? extends BaseMapper<T>> mapperClass, Collection<T> data, int batchSize) {
        return execute(data, batchSize, (session, item) -> session.getMapper(mapperClass).insert(item));
    }

    public <T> int saveBatch(Class<? extends BaseMapper<T>> mapperClass, Collection<T> data) {
        return saveBatch(mapperClass, data, DEFAULT_BATCH_SIZE);
    }

    public <T> int execute(Collection<T> data, int batchSize, BiConsumer<SqlSession, T> action) {
        if (data == null || data.isEmpty()) {
            return 0;
        }
        int size = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return flush(transactionSession(), data, size, action, false);
        }
        try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
            return flush(session, data, size, action, true);
        }
    }

    // 事务中已绑定的会话可能不是 BATCH 执行器，必须原样复用，换执行器类型 mybatis-spring 会直接抛异常
    private SqlSession transactionSession() {
        SqlSessionHolder holder = (SqlSessionHolder) TransactionSynchronizationManager.getResource(sqlSessionFactory);
        if (holder != null) {
            return holder.getSqlSession();
        }
        return SqlSessionUtils.getSqlSession(sqlSessionFactory, ExecutorType.BATCH, null);
    }

    private <T> int flush(SqlSession session, Collection<T> data, int size, BiConsumer<SqlSession, T> action,
                          boolean ownTransaction) {
        int total = 0;
        int pending = 0;
        for (T item : data) {
            action.accept(session, item);
            pending++;
            if (pending >= size) {
                commitSegment(session, ownTransaction);
                total += pending;
                pending = 0;
            }
        }
        if (pending > 0) {
            commitSegment(session, ownTransaction);
            total += pending;
        }
        return total;
    }

    private static void commitSegment(SqlSession session, boolean ownTransaction) {
        session.flushStatements();
        if (ownTransaction) {
            session.commit();
        }
    }
}
