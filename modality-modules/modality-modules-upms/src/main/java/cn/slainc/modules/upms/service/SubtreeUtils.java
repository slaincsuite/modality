package cn.slainc.modules.upms.service;

import cn.slainc.core.exception.BizException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class SubtreeUtils {

    private SubtreeUtils() {
    }

    public static Set<Long> collectSubtree(Long rootId, Function<Long, List<Long>> childrenIdsOf) {
        Set<Long> ids = new LinkedHashSet<>();
        collect(rootId, ids, childrenIdsOf);
        return ids;
    }

    // 只查「上级≠自身」挡不住把父节点挂到自己的子孙下：改完即成环，后续递归再也出不来
    public static void requireValidParent(Set<Long> subtreeIds, Long selfId, Long parentId, String label) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new BizException(label + "的上级不能是自身");
        }
        if (subtreeIds.contains(parentId)) {
            throw new BizException(label + "的上级不能是自身的下级");
        }
    }

    private static void collect(Long nodeId, Set<Long> ids, Function<Long, List<Long>> childrenIdsOf) {
        if (nodeId == null || ids.contains(nodeId)) {
            return;
        }
        ids.add(nodeId);
        for (Long childId : childrenIdsOf.apply(nodeId)) {
            collect(childId, ids, childrenIdsOf);
        }
    }
}
