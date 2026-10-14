package cn.slainc.modules.infra.autoconfigure;

import cn.slainc.modules.infra.context.CurrentUser;
import cn.slainc.modules.infra.mapper.SearchDocumentMapper;
import cn.slainc.modules.infra.mapper.SearchHistoryMapper;
import cn.slainc.modules.infra.mapper.SearchIndexTermMapper;
import cn.slainc.modules.infra.service.SearchDocumentService;
import cn.slainc.modules.infra.service.SearchHistoryService;
import cn.slainc.modules.infra.service.SearchManagementService;
import cn.slainc.modules.infra.web.SearchController;
import cn.slainc.modules.infra.web.SearchHistoryController;
import cn.slainc.search.api.SearchEngine;
import cn.slainc.search.config.SearchProperties;
import cn.slainc.search.index.SearchIndexer;
import cn.slainc.search.source.SearchDocumentRegistry;
import cn.slainc.search.source.SearchIndexRefresher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = "cn.slainc.search.autoconfigure.SearchAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.search", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InfraSearchAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SearchHistoryService searchHistoryService(ObjectProvider<SearchHistoryMapper> historyMapper,
                                                     ObjectProvider<CurrentUser> currentUser) {
        return new SearchHistoryService(historyMapper.getIfAvailable(), currentUser.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public SearchDocumentService searchDocumentService(ObjectProvider<SearchDocumentMapper> documentMapper,
                                                       ObjectProvider<SearchIndexTermMapper> termMapper,
                                                       ObjectProvider<SearchIndexer> searchIndexer,
                                                       ObjectProvider<SearchDocumentRegistry> registry,
                                                       ObjectProvider<SearchProperties> searchProperties) {
        return new SearchDocumentService(documentMapper.getIfAvailable(), termMapper.getIfAvailable(),
                searchIndexer.getIfAvailable(), registry.getIfAvailable(), searchProperties.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SearchEngine.class)
    public SearchManagementService searchManagementService(SearchEngine searchEngine,
                                                           SearchDocumentService documentService,
                                                           ObjectProvider<SearchIndexRefresher> indexRefresher,
                                                           SearchHistoryService historyService,
                                                           ObjectProvider<SearchProperties> searchProperties,
                                                           ObjectProvider<CurrentUser> currentUser) {
        return new SearchManagementService(searchEngine, documentService, indexRefresher.getIfAvailable(),
                historyService, searchProperties.getIfAvailable(), currentUser.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SearchManagementService.class)
    public SearchController searchController(SearchManagementService searchManagementService) {
        return new SearchController(searchManagementService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SearchHistoryService.class)
    public SearchHistoryController searchHistoryController(SearchHistoryService searchHistoryService) {
        return new SearchHistoryController(searchHistoryService);
    }
}
