package cn.slainc.utils.tree;

import java.util.List;

public interface TreeNode<T extends TreeNode<T, ID>, ID> {

    ID getId();

    ID getParentId();

    List<T> getChildren();

    void setChildren(List<T> children);

    default boolean isRoot() {
        return getParentId() == null;
    }
}
