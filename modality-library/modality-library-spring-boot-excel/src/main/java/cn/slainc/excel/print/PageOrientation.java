package cn.slainc.excel.print;

public enum PageOrientation {

    PORTRAIT("纵向"),
    LANDSCAPE("横向");

    private final String description;

    PageOrientation(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public boolean landscape() {
        return this == LANDSCAPE;
    }
}
