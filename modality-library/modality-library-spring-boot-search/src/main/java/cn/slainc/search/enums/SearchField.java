package cn.slainc.search.enums;

public enum SearchField {

    TITLE(5),

    TAGS(3),

    SUMMARY(2),

    CONTENT(1);

    private final int weight;

    SearchField(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
