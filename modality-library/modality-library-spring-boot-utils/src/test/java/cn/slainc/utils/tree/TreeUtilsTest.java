package cn.slainc.utils.tree;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeUtilsTest {

    static class Node implements TreeNode<Node, Long> {

        private final Long id;
        private final Long parentId;
        private final String name;
        private List<Node> children = new ArrayList<>();

        Node(Long id, Long parentId, String name) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
        }

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public Long getParentId() {
            return parentId;
        }

        @Override
        public List<Node> getChildren() {
            return children;
        }

        @Override
        public void setChildren(List<Node> children) {
            this.children = children;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private List<Node> flatNodes() {
        return new ArrayList<>(List.of(
                new Node(1L, null, "root"),
                new Node(2L, 1L, "child-a"),
                new Node(3L, 1L, "child-b"),
                new Node(4L, 2L, "grandchild")));
    }

    @Test
    void buildHierarchy() {
        List<Node> roots = TreeUtils.build(flatNodes());
        assertEquals(1, roots.size());
        assertEquals(1L, roots.get(0).getId());
        assertEquals(2, roots.get(0).getChildren().size());
        assertEquals(1, roots.get(0).getChildren().get(0).getChildren().size());
        assertEquals(3, TreeUtils.depth(roots));
    }

    @Test
    void traversalAndSearch() {
        List<Node> roots = TreeUtils.build(flatNodes());
        List<Long> visited = new ArrayList<>();
        TreeUtils.traverseDepthFirst(roots, node -> visited.add(node.getId()));
        assertEquals(List.of(1L, 2L, 4L, 3L), visited);

        List<Long> breadth = new ArrayList<>();
        TreeUtils.traverseBreadthFirst(roots, node -> breadth.add(node.getId()));
        assertEquals(List.of(1L, 2L, 3L, 4L), breadth);

        assertNotNull(TreeUtils.findById(roots, 4L));
        assertNull(TreeUtils.findById(roots, 99L));
        assertEquals(2, TreeUtils.findAll(roots, node -> node.getParentId() != null
                && node.getParentId().equals(1L)).size());
        assertEquals(4, TreeUtils.flat(roots).size());
    }

    @Test
    void pruneKeepsAncestors() {
        List<Node> roots = TreeUtils.build(flatNodes());
        List<Node> pruned = TreeUtils.prune(roots, node -> node.getId().equals(4L));
        assertEquals(1, pruned.size());
        assertEquals(1, pruned.get(0).getChildren().size());
        assertEquals(2L, pruned.get(0).getChildren().get(0).getId());
        assertTrue(pruned.get(0).getChildren().get(0).getChildren().isEmpty() == false);
    }

    @Test
    void sortAndCollectIds() {
        List<Node> roots = TreeUtils.build(flatNodes());
        TreeUtils.sort(roots, (left, right) -> Long.compare(right.getId(), left.getId()));
        assertEquals(1L, roots.get(0).getId());
        assertEquals(3L, roots.get(0).getChildren().get(0).getId());
        assertEquals(List.of(1L, 3L, 2L, 4L), TreeUtils.collectIds(roots));
    }
}
