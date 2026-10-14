package cn.slainc.changeaudit.support;

import java.io.Serializable;

public record FieldChange(String field, String before, String after) implements Serializable {

    private static final long serialVersionUID = 1L;
}
