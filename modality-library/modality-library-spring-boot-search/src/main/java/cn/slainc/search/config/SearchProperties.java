package cn.slainc.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "modality.search")
public class SearchProperties {

    public static final String ENGINE_LUCENE = "lucene";

    private boolean enabled = true;

    private String engine = ENGINE_LUCENE;

    private String directory;

    private long refreshIntervalMs = 1000L;

    private int defaultPageSize = 10;

    private int maxPageSize = 100;

    private int maxContentLength = 20000;

    private int snippetLength = 160;

    private int minTermLength = 1;

    private List<String> stopWords = new ArrayList<>();

    private double k1 = 1.2D;

    private double b = 0.75D;

    private int reindexBatchSize = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = ENGINE_LUCENE.equalsIgnoreCase(engine == null ? "" : engine.trim()) ? ENGINE_LUCENE : engine;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public long getRefreshIntervalMs() {
        return refreshIntervalMs;
    }

    public void setRefreshIntervalMs(long refreshIntervalMs) {
        this.refreshIntervalMs = refreshIntervalMs;
    }

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public int getSnippetLength() {
        return snippetLength;
    }

    public void setSnippetLength(int snippetLength) {
        this.snippetLength = snippetLength;
    }

    public int getMinTermLength() {
        return minTermLength;
    }

    public void setMinTermLength(int minTermLength) {
        this.minTermLength = minTermLength;
    }

    public List<String> getStopWords() {
        return stopWords;
    }

    public void setStopWords(List<String> stopWords) {
        this.stopWords = stopWords == null ? new ArrayList<>() : stopWords;
    }

    public double getK1() {
        return k1;
    }

    public void setK1(double k1) {
        this.k1 = k1;
    }

    public double getB() {
        return b;
    }

    public void setB(double b) {
        this.b = b;
    }

    public int getReindexBatchSize() {
        return reindexBatchSize;
    }

    public void setReindexBatchSize(int reindexBatchSize) {
        this.reindexBatchSize = reindexBatchSize;
    }
}
