package cn.slainc.session.query;

import java.util.List;
import java.util.Map;

public interface SessionTokenBatchReader {

    Map<String, String> getMany(List<String> keys);

    Map<String, Long> getTimeouts(List<String> keys);
}
