package cn.slainc.excel.print;

public enum PageOrientation {

    PORTRAIT,
    LANDSCAPE;

    public boolean landscape() {
        return this == LANDSCAPE;
    }
}
