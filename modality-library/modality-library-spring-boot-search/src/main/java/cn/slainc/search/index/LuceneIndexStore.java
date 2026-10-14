package cn.slainc.search.index;

import cn.slainc.search.analyzer.SearchAnalyzer;
import cn.slainc.search.spi.SearchStats;
import cn.slainc.search.config.SearchProperties;
import cn.slainc.search.spi.SearchDocumentSnapshot;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class LuceneIndexStore implements Closeable {

    public static final String FIELD_ID = "id";

    public static final String FIELD_ID_EXACT = "idExact";

    public static final String FIELD_SOURCE_TYPE = "sourceType";

    public static final String FIELD_SOURCE_ID = "sourceId";

    public static final String FIELD_TENANT_ID = "tenantId";

    public static final String FIELD_TITLE = "title";

    public static final String FIELD_SUMMARY = "summary";

    public static final String FIELD_CONTENT = "content";

    public static final String FIELD_TAGS = "tags";

    private static final String[] TEXT_FIELDS = {FIELD_TITLE, FIELD_SUMMARY, FIELD_CONTENT, FIELD_TAGS};

    public static final String COMMIT_LAST_INDEXED_AT = "modality.search.lastIndexedAt";

    private static final String DEFAULT_APP_NAME = "default";

    private static final double DEFAULT_K1 = 1.2D;

    private static final double DEFAULT_B = 0.75D;

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final Logger LOGGER = LoggerFactory.getLogger(LuceneIndexStore.class);

    private final SearchProperties properties;

    private final SearchAnalyzer analyzer;

    private final Directory directory;

    private final IndexWriter writer;

    private final SearcherManager searcherManager;

    private final ScheduledExecutorService committer;

    private final AtomicReference<LocalDateTime> lastIndexedAt = new AtomicReference<>();

    public LuceneIndexStore(SearchProperties properties, SearchAnalyzer analyzer, String appName) throws IOException {
        SearchProperties source = properties == null ? new SearchProperties() : properties;
        this.properties = source;
        this.analyzer = analyzer == null ? new SearchAnalyzer(source) : analyzer;
        this.directory = openDirectory(resolvePath(source.getDirectory(), appName));
        IndexWriterConfig config = new IndexWriterConfig(this.analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        IndexWriter openedWriter = null;
        SearcherManager openedManager = null;
        try {
            openedWriter = new IndexWriter(this.directory, config);
            openedManager = new SearcherManager(openedWriter, new SearchSearcherFactory(source));
        } catch (IOException e) {
            closeQuietly(openedManager, "searcher 管理器");
            closeQuietly(openedWriter, "索引写入器");
            closeQuietly(this.directory, "索引目录");
            throw new IOException("打开检索索引失败: " + this.directory
                    + "（同一索引目录只允许一个写入实例，可用 modality.search.directory 指定独立目录）", e);
        }
        this.writer = openedWriter;
        this.searcherManager = openedManager;
        this.lastIndexedAt.set(readLastIndexedAt());
        long interval = Math.max(200L, source.getRefreshIntervalMs());
        this.committer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "modality-search-commit");
            thread.setDaemon(true);
            return thread;
        });
        this.committer.scheduleWithFixedDelay(this::commitQuietly, interval, interval, TimeUnit.MILLISECONDS);
        LOGGER.info("检索索引已打开: {}，提交间隔 {}ms", this.directory, interval);
    }

    public void upsert(SearchDocumentSnapshot document) throws IOException {
        Document doc = toDocument(document);
        writer.updateDocument(new Term(FIELD_ID_EXACT, String.valueOf(document.id())), doc);
        touch();
        refresh();
    }

    public int delete(Long documentId) throws IOException {
        if (documentId == null) {
            return 0;
        }
        Term term = new Term(FIELD_ID_EXACT, String.valueOf(documentId));
        int existing = count(term);
        writer.deleteDocuments(term);
        touch();
        refresh();
        return existing > 0 ? 1 : 0;
    }

    public <T> T search(SearcherOperation<T> operation) {
        IndexSearcher searcher;
        try {
            searcher = searcherManager.acquire();
        } catch (IOException e) {
            LOGGER.warn("获取检索 reader 失败: {}", e.getMessage());
            return null;
        }
        try {
            return operation.apply(searcher);
        } catch (IOException e) {
            LOGGER.warn("执行检索失败: {}", e.getMessage());
            return null;
        } finally {
            release(searcher);
        }
    }

    private int count(Term term) {
        Integer count = search(searcher -> searcher.count(new TermQuery(term)));
        return count == null ? 0 : count;
    }

    public SearchStats stats() {
        IndexSearcher searcher;
        try {
            searcher = searcherManager.acquire();
        } catch (IOException e) {
            LOGGER.warn("获取检索 reader 失败: {}", e.getMessage());
            return SearchStats.empty();
        }
        try {
            IndexReader reader = searcher.getIndexReader();
            return new SearchStats(reader.numDocs(), termCount(reader), lastIndexedAt.get());
        } catch (IOException e) {
            LOGGER.warn("统计索引信息失败: {}", e.getMessage());
            return SearchStats.empty();
        } finally {
            release(searcher);
        }
    }

    public SearchAnalyzer analyzer() {
        return analyzer;
    }

    @Override
    public void close() {
        committer.shutdownNow();
        try {
            // 先等在途提交线程退出再接管写入器，避免并发提交/关闭使文件锁失效
            if (!committer.awaitTermination(10, TimeUnit.SECONDS)) {
                LOGGER.warn("等待检索索引提交线程退出超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            commitInternal();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("关闭前提交检索索引失败: {}", e.getMessage());
        }
        closeQuietly(searcherManager, "searcher 管理器");
        closeQuietly(writer, "索引写入器");
        closeQuietly(directory, "索引目录");
        LOGGER.info("检索索引已关闭: {}", directory);
    }

    private void commitInternal() throws IOException {
        LocalDateTime indexedAt = lastIndexedAt.get();
        if (indexedAt != null) {
            writer.setLiveCommitData(Map.of(COMMIT_LAST_INDEXED_AT, TIMESTAMP.format(indexedAt)).entrySet());
        }
        writer.commit();
    }

    private void commitQuietly() {
        try {
            if (writer.hasUncommittedChanges()) {
                commitInternal();
                LOGGER.debug("检索索引已提交，最近索引时间 {}", lastIndexedAt.get());
            }
        } catch (IOException e) {
            LOGGER.warn("提交检索索引失败: {}", e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.warn("提交检索索引失败: {}", e.getMessage());
        }
    }

    private void touch() {
        lastIndexedAt.set(LocalDateTime.now());
    }

    private void refresh() throws IOException {
        searcherManager.maybeRefreshBlocking();
    }

    private void release(IndexSearcher searcher) {
        try {
            searcherManager.release(searcher);
        } catch (IOException e) {
            LOGGER.debug("释放检索 reader 失败: {}", e.getMessage());
        }
    }

    private static long termCount(IndexReader reader) throws IOException {
        long count = 0L;
        for (LeafReaderContext context : reader.leaves()) {
            for (String field : TEXT_FIELDS) {
                Terms terms = context.reader().terms(field);
                if (terms != null) {
                    count += terms.getSumDocFreq();
                }
            }
        }
        return count;
    }

    private Document toDocument(SearchDocumentSnapshot document) {
        Document doc = new Document();
        doc.add(new StoredField(FIELD_ID, document.id()));
        doc.add(new StringField(FIELD_ID_EXACT, String.valueOf(document.id()), Field.Store.NO));
        addKeyword(doc, FIELD_SOURCE_TYPE, document.sourceType());
        addKeyword(doc, FIELD_SOURCE_ID, document.sourceId());
        if (document.tenantId() != null) {
            doc.add(new StringField(FIELD_TENANT_ID, String.valueOf(document.tenantId()), Field.Store.NO));
        }
        addText(doc, FIELD_TITLE, document.title());
        addText(doc, FIELD_SUMMARY, document.summary());
        addText(doc, FIELD_CONTENT, truncate(document.content()));
        addText(doc, FIELD_TAGS, document.tags());
        return doc;
    }

    private static void addText(Document doc, String field, String value) {
        if (value == null || value.isBlank()) {
            doc.add(new StoredField(field, ""));
            return;
        }
        doc.add(new TextField(field, value, Field.Store.YES));
    }

    private static void addKeyword(Document doc, String field, String value) {
        if (value == null || value.isBlank()) {
            doc.add(new StoredField(field, ""));
            return;
        }
        doc.add(new StringField(field, value.trim(), Field.Store.YES));
    }

    private String truncate(String content) {
        if (content == null) {
            return null;
        }
        int maxLength = Math.max(1, properties.getMaxContentLength());
        return content.length() > maxLength ? content.substring(0, maxLength) : content;
    }

    private LocalDateTime readLastIndexedAt() {
        try {
            if (!DirectoryReader.indexExists(directory)) {
                return null;
            }
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                String value = reader.getIndexCommit().getUserData().get(COMMIT_LAST_INDEXED_AT);
                return value == null || value.isBlank() ? null : LocalDateTime.parse(value, TIMESTAMP);
            }
        } catch (IOException | DateTimeParseException e) {
            LOGGER.debug("读取最近索引时间失败: {}", e.getMessage());
            return null;
        }
    }

    private static Directory openDirectory(Path path) throws IOException {
        try {
            Files.createDirectories(path);
            return FSDirectory.open(path);
        } catch (IOException e) {
            throw new IOException("打开检索索引目录失败: " + path, e);
        }
    }

    private static Path resolvePath(String configured, String appName) {
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim()).toAbsolutePath();
        }
        String name = appName == null || appName.isBlank() ? DEFAULT_APP_NAME : appName.trim();
        return Path.of(System.getProperty("java.io.tmpdir"), "modality", "search", sanitize(name)).toAbsolutePath();
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static float k1(SearchProperties properties) {
        double k1 = properties.getK1();
        return (float) (k1 < 0D ? DEFAULT_K1 : k1);
    }

    private static float b(SearchProperties properties) {
        double b = properties.getB();
        return (float) (b < 0D || b > 1D ? DEFAULT_B : b);
    }

    private static void closeQuietly(Closeable closeable, String description) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            LOGGER.warn("关闭检索索引 {} 失败: {}", description, e.getMessage());
        }
    }

    @FunctionalInterface
    public interface SearcherOperation<T> {

        T apply(IndexSearcher searcher) throws IOException;
    }

    private static final class SearchSearcherFactory extends SearcherFactory {

        private final SearchProperties properties;

        private SearchSearcherFactory(SearchProperties properties) {
            this.properties = properties;
        }

        @Override
        public IndexSearcher newSearcher(IndexReader reader, IndexReader previousReader) throws IOException {
            IndexSearcher searcher = super.newSearcher(reader, previousReader);
            searcher.setSimilarity(new BM25Similarity(k1(properties), b(properties)));
            return searcher;
        }
    }
}
