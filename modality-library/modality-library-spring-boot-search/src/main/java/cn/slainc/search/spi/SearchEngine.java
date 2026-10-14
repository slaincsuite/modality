package cn.slainc.search.spi;

import cn.slainc.core.result.PageResult;

public interface SearchEngine {

    PageResult<SearchHit> search(SearchQuery query);

    SearchStats stats();
}
