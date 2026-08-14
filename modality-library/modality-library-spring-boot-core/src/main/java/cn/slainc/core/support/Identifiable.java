package cn.slainc.core.support;

import java.io.Serializable;

public interface Identifiable<ID> extends Serializable {

    ID getId();
}
