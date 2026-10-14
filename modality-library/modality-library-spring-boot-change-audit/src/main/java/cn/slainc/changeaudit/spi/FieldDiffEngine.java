package cn.slainc.changeaudit.spi;

import cn.slainc.changeaudit.support.FieldChange;

import java.util.List;

public interface FieldDiffEngine {

    List<FieldChange> diff(Object before, Object after);

    String serialize(List<FieldChange> changes);
}
