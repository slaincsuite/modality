package cn.slainc.utils.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class TreeUtils {

    private TreeUtils() {
    }

    public static <T extends TreeNode<T, ID>, ID> List<T> build(List<T> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return new ArrayList<>();
        }
        Map<ID, T> indexed = new HashMap<>();
        for (T node : nodes) {
            node.setChildren(new ArrayList<>());
            indexed.put(node.getId(), node);
        }
        List<T> roots = new ArrayList<>();
        for (T node : nodes) {
            ID parentId = node.getParentId();
            T parent = parentId == null ? null : indexed.get(parentId);
            if (parent == null || parent == node) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    public static <T extends TreeNode<T, ID>, ID> List<T> buildAndSort(List<T> nodes, Comparator<T> comparator) {
        List<T> roots = build(nodes);
        sort(roots, comparator);
        return roots;
    }

    public static <T extends TreeNode<T, ID>, ID> void sort(List<T> nodes, Comparator<T> comparator) {
        if (nodes == null || nodes.isEmpty() || comparator == null) {
            return;
        }
        nodes.sort(comparator);
        for (T node : nodes) {
            sort(node.getChildren(), comparator);
        }
    }

    public static <T extends TreeNode<T, ID>, ID> void traverseDepthFirst(List<T> roots, Consumer<T> visitor) {
        if (roots == null || visitor == null) {
            return;
        }
        for (T root : roots) {
            visitor.accept(root);
            traverseDepthFirst(root.getChildren(), visitor);
        }
    }

    public static <T extends TreeNode<T, ID>, ID> void traverseBreadthFirst(List<T> roots, Consumer<T> visitor) {
        if (roots == null || visitor == null) {
            return;
        }
        Deque<T> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            T node = queue.poll();
            visitor.accept(node);
            List<T> children = node.getChildren();
            if (children != null) {
                queue.addAll(children);
            }
        }
    }

    public static <T extends TreeNode<T, ID>, ID> T findById(List<T> roots, ID id) {
        return find(roots, node -> Objects.equals(node.getId(), id));
    }

    public static <T extends TreeNode<T, ID>, ID> T find(List<T> roots, Predicate<T> predicate) {
        if (roots == null || predicate == null) {
            return null;
        }
        Deque<T> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            T node = queue.poll();
            if (predicate.test(node)) {
                return node;
            }
            List<T> children = node.getChildren();
            if (children != null) {
                queue.addAll(children);
            }
        }
        return null;
    }

    public static <T extends TreeNode<T, ID>, ID> List<T> findAll(List<T> roots, Predicate<T> predicate) {
        List<T> result = new ArrayList<>();
        traverseBreadthFirst(roots, node -> {
            if (predicate.test(node)) {
                result.add(node);
            }
        });
        return result;
    }

    public static <T extends TreeNode<T, ID>, ID> List<T> prune(List<T> roots, Predicate<T> keep) {
        List<T> result = new ArrayList<>();
        if (roots == null || keep == null) {
            return result;
        }
        for (T root : roots) {
            T pruned = pruneNode(root, keep);
            if (pruned != null) {
                result.add(pruned);
            }
        }
        return result;
    }

    private static <T extends TreeNode<T, ID>, ID> T pruneNode(T node, Predicate<T> keep) {
        List<T> children = node.getChildren();
        List<T> keptChildren = new ArrayList<>();
        if (children != null) {
            for (T child : children) {
                T pruned = pruneNode(child, keep);
                if (pruned != null) {
                    keptChildren.add(pruned);
                }
            }
        }
        if (keptChildren.isEmpty() && !keep.test(node)) {
            return null;
        }
        node.setChildren(keptChildren);
        return node;
    }

    public static <T extends TreeNode<T, ID>, ID> List<ID> collectIds(List<T> roots) {
        Set<ID> ids = new LinkedHashSet<>();
        traverseBreadthFirst(roots, node -> ids.add(node.getId()));
        return new ArrayList<>(ids);
    }

    public static <T extends TreeNode<T, ID>, ID> List<T> flat(List<T> roots) {
        List<T> result = new ArrayList<>();
        traverseBreadthFirst(roots, result::add);
        return result;
    }

    public static <T extends TreeNode<T, ID>, ID> int depth(List<T> roots) {
        if (roots == null || roots.isEmpty()) {
            return 0;
        }
        int max = 0;
        for (T root : roots) {
            max = Math.max(max, 1 + depth(root.getChildren()));
        }
        return max;
    }
}
