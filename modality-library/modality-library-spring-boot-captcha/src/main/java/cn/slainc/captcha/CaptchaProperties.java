package cn.slainc.captcha;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "modality.captcha")
public class CaptchaProperties {

    private boolean enabled = true;

    private String type = CaptchaType.CHAR_ALNUM.name();

    private int length = 4;

    private int ttlSeconds = 300;

    private int failThreshold = 3;

    private int ipLimitPerMinute = 30;

    private int globalLimitPerMinute = 1000;

    private String store = "redis";

    private List<String> ipBlacklist = new ArrayList<>();

    private final Image image = new Image();

    private final Slide slide = new Slide();

    private final Click click = new Click();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public int getFailThreshold() {
        return failThreshold;
    }

    public void setFailThreshold(int failThreshold) {
        this.failThreshold = failThreshold;
    }

    public int getIpLimitPerMinute() {
        return ipLimitPerMinute;
    }

    public void setIpLimitPerMinute(int ipLimitPerMinute) {
        this.ipLimitPerMinute = ipLimitPerMinute;
    }

    public int getGlobalLimitPerMinute() {
        return globalLimitPerMinute;
    }

    public void setGlobalLimitPerMinute(int globalLimitPerMinute) {
        this.globalLimitPerMinute = globalLimitPerMinute;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public List<String> getIpBlacklist() {
        return ipBlacklist;
    }

    public void setIpBlacklist(List<String> ipBlacklist) {
        this.ipBlacklist = ipBlacklist == null ? new ArrayList<>() : ipBlacklist;
    }

    public Image getImage() {
        return image;
    }

    public Slide getSlide() {
        return slide;
    }

    public Click getClick() {
        return click;
    }

    public static class Image {

        private int width = 130;

        private int height = 48;

        private int fontIndex = 0;

        private float fontSize = 32f;

        private Integer charType;

        private String background = "#FFFFFF";

        private int ovalCount = 2;

        private int lineCount = 0;

        private int besselCount = 1;

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public int getFontIndex() {
            return fontIndex;
        }

        public void setFontIndex(int fontIndex) {
            this.fontIndex = fontIndex;
        }

        public float getFontSize() {
            return fontSize;
        }

        public void setFontSize(float fontSize) {
            this.fontSize = fontSize;
        }

        public Integer getCharType() {
            return charType;
        }

        public void setCharType(Integer charType) {
            this.charType = charType;
        }

        public String getBackground() {
            return background;
        }

        public void setBackground(String background) {
            this.background = background;
        }

        public int getOvalCount() {
            return ovalCount;
        }

        public void setOvalCount(int ovalCount) {
            this.ovalCount = ovalCount;
        }

        public int getLineCount() {
            return lineCount;
        }

        public void setLineCount(int lineCount) {
            this.lineCount = lineCount;
        }

        public int getBesselCount() {
            return besselCount;
        }

        public void setBesselCount(int besselCount) {
            this.besselCount = besselCount;
        }
    }

    public static class Slide {

        private int width = 280;

        private int height = 150;

        private int pieceSize = 44;

        private int tolerancePx = 5;

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public int getPieceSize() {
            return pieceSize;
        }

        public void setPieceSize(int pieceSize) {
            this.pieceSize = pieceSize;
        }

        public int getTolerancePx() {
            return tolerancePx;
        }

        public void setTolerancePx(int tolerancePx) {
            this.tolerancePx = tolerancePx;
        }
    }

    public static class Click {

        private int width = 300;

        private int height = 160;

        private int charCount = 4;

        private int tolerancePx = 15;

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public int getCharCount() {
            return charCount;
        }

        public void setCharCount(int charCount) {
            this.charCount = charCount;
        }

        public int getTolerancePx() {
            return tolerancePx;
        }

        public void setTolerancePx(int tolerancePx) {
            this.tolerancePx = tolerancePx;
        }
    }
}
