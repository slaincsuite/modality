package cn.slainc.mybatis.batch;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

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

    public <T> int updateBatchById(Class<? extends BaseMapper<T>> mapperClass, Collection<T> data, int batchSize) {
        return execute(data, batchSize, (session, item) -> session.getMapper(mapperClass).updateById(item));
    }

    public <T> int updateBatchById(Class<? extends BaseMapper<T>> mapperClass, Collection<T> data) {
        return updateBatchById(mapperClass, data, DEFAULT_BATCH_SIZE);
    }

    public <T> int deleteBatchById(Class<? extends BaseMapper<T>> mapperClass, Collection<T> ids, int batchSize) {
        return execute(ids, batchSize, (session, id) -> session.getMapper(mapperClass).deleteById(id));
    }

    public <T> int execute(Collection<T> data, int batchSize, BiConsumer<SqlSession, T> action) {
        if (data == null || data.isEmpty()) {
            return 0;
        }
        int size = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        int total = 0;
        int pending = 0;
        try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
            for (T item : data) {
                action.accept(session, item);
                pending++;
                if (pending >= size) {
                    session.flushStatements();
                    session.commit();
                    total += pending;
                    pending = 0;
                }
            }
            if (pending > 0) {
                session.flushStatements();
                session.commit();
                total += pending;
            }
        }
        return total;
    }
}
