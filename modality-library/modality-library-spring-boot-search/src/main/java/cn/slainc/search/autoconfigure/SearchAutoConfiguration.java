package cn.slainc.search.autoconfigure;

import cn.slainc.search.analyzer.SearchAnalyzer;
import cn.slainc.search.api.SearchEngine;
import cn.slainc.search.support.LuceneSearchEngine;
import cn.slainc.search.config.SearchProperties;
import cn.slainc.search.index.LuceneIndexStore;
import cn.slainc.search.index.SearchIndexer;
import cn.slainc.search.source.SearchDocumentRegistry;
import cn.slainc.search.source.SearchDocumentSource;
import cn.slainc.search.source.SearchIndexRefresher;
import cn.slainc.search.spi.SearchDocumentPort;
import cn.slainc.search.support.Bm25Scorer;
import cn.slainc.search.support.SearchQueryParser;
import cn.slainc.search.support.SnippetHighlighter;
import cn.slainc.search.tokenizer.SearchTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.io.IOException;

@AutoConfiguration(afterName = "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration")
@EnableConfigurationProperties(SearchProperties.class)
@ConditionalOnProperty(prefix = "modality.search", name = "enabled", matchIfMissing = true)
public class SearchAutoConfiguration {

    private static final String APP_NAME_KEY = "spring.application.name";

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(SearchTokenizer.class)
    public SearchTokenizer searchTokenizer(SearchProperties properties) {
        return new SearchTokenizer(properties);
    }

    @Bean
    @ConditionalOnMissingBean(Bm25Scorer.class)
    public Bm25Scorer bm25Scorer(SearchProperties properties) {
        return new Bm25Scorer(properties.getK1(), properties.getB());
    }

    @Bean
    @ConditionalOnMissingBean(SnippetHighlighter.class)
    public SnippetHighlighter snippetHighlighter(SearchProperties properties) {
        return new SnippetHighlighter(properties);
    }

    @Bean
    @ConditionalOnMissingBean(SearchQueryParser.class)
    public SearchQueryParser searchQueryParser(SearchTokenizer searchTokenizer) {
        return new SearchQueryParser(searchTokenizer);
    }

    @Bean
    @ConditionalOnMissingBean(SearchAnalyzer.class)
    public SearchAnalyzer searchAnalyzer(SearchProperties properties) {
        return new SearchAnalyzer(properties);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(LuceneIndexStore.class)
    public LuceneIndexStore luceneIndexStore(SearchProperties properties, SearchAnalyzer searchAnalyzer,
                                             Environment environment) throws IOException {
        String appName = environment.getProperty(APP_NAME_KEY, "");
        LOGGER.debug("以应用名 {} 打开内嵌检索索引", appName);
        return new LuceneIndexStore(properties, searchAnalyzer, appName);
    }

    @Bean
    @ConditionalOnMissingBean(SearchIndexer.class)
    public SearchIndexer searchIndexer(ObjectProvider<SearchDocumentPort> portProvider,
                                       SearchTokenizer searchTokenizer, SearchProperties properties,
                                       ObjectProvider<LuceneIndexStore> indexStoreProvider) {
        return new SearchIndexer(portProvider, searchTokenizer, properties, indexStoreProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(SearchDocumentRegistry.class)
    public SearchDocumentRegistry searchDocumentRegistry(ObjectProvider<SearchDocumentSource> sources) {
        return new SearchDocumentRegistry(sources.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean(SearchEngine.class)
    public SearchEngine searchEngine(SearchProperties properties, SearchQueryParser searchQueryParser,
                                     ObjectProvider<LuceneIndexStore> indexStoreProvider) {
        return new LuceneSearchEngine(indexStoreProvider.getIfAvailable(), searchQueryParser, properties);
    }

    @Bean
    @ConditionalOnMissingBean(SearchIndexRefresher.class)
    public SearchIndexRefresher searchIndexRefresher(SearchDocumentRegistry registry,
                                                     ObjectProvider<SearchDocumentPort> portProvider) {
        return new SearchIndexRefresher(registry, portProvider);
    }
}
