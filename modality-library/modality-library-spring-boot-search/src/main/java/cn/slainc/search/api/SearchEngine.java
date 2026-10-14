package cn.slainc.search.api;

import cn.slainc.core.result.PageResult;

public interface SearchEngine {

    PageResult<SearchHit> search(SearchQuery query);

    SearchStats stats();
}
