package cn.slainc.session;

import cn.slainc.core.enums.LoginType;

import java.time.LocalDateTime;

public record OnlineQuery(LoginType type, String tenantId, String deviceType, LocalDateTime loginAfter,
                          LocalDateTime loginBefore, String keyword, int page, int size,
                          SessionSortField sortField, boolean descending) {

    public static final int DEFAULT_SIZE = 20;

    public static final int MAX_SIZE = 500;

    public OnlineQuery {
        page = page <= 0 ? 1 : page;
        size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        sortField = sortField == null ? SessionSortField.LOGIN_TIME : sortField;
    }

    public static OnlineQuery all() {
        return new OnlineQuery(null, null, null, null, null, null, 1, DEFAULT_SIZE,
                SessionSortField.LOGIN_TIME, true);
    }

    public static OnlineQuery of(LoginType type) {
        return new OnlineQuery(type, null, null, null, null, null, 1, DEFAULT_SIZE,
                SessionSortField.LOGIN_TIME, true);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private LoginType type;

        private String tenantId;

        private String deviceType;

        private LocalDateTime loginAfter;

        private LocalDateTime loginBefore;

        private String keyword;

        private int page = 1;

        private int size = DEFAULT_SIZE;

        private SessionSortField sortField = SessionSortField.LOGIN_TIME;

        private boolean descending = true;

        public Builder type(LoginType value) {
            this.type = value;
            return this;
        }

        public Builder tenantId(String value) {
            this.tenantId = value;
            return this;
        }

        public Builder deviceType(String value) {
            this.deviceType = value;
            return this;
        }

        public Builder loginAfter(LocalDateTime value) {
            this.loginAfter = value;
            return this;
        }

        public Builder loginBefore(LocalDateTime value) {
            this.loginBefore = value;
            return this;
        }

        public Builder keyword(String value) {
            this.keyword = value;
            return this;
        }

        public Builder page(int value) {
            this.page = value;
            return this;
        }

        public Builder size(int value) {
            this.size = value;
            return this;
        }

        public Builder sortField(SessionSortField value) {
            this.sortField = value;
            return this;
        }

        public Builder descending(boolean value) {
            this.descending = value;
            return this;
        }

        public OnlineQuery build() {
            return new OnlineQuery(type, tenantId, deviceType, loginAfter, loginBefore, keyword, page, size,
                    sortField, descending);
        }
    }
}
