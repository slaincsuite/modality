package cn.slainc.sensitive.demo;

import cn.slainc.sensitive.annotation.Sensitive;
import cn.slainc.sensitive.annotation.SensitiveType;

public class CycleNode {

    private String name;

    @Sensitive(type = SensitiveType.PHONE)
    private String phone;

    private CycleNode next;

    private CycleNode peer;

    public CycleNode(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    public static CycleNode selfCycle() {
        CycleNode node = new CycleNode("自环节点", "13800001111");
        node.next = node;
        node.peer = node;
        return node;
    }

    public static CycleNode mutualCycle() {
        CycleNode first = new CycleNode("甲", "13800002222");
        CycleNode second = new CycleNode("乙", "13800003333");
        first.peer = second;
        second.peer = first;
        return first;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public CycleNode getNext() {
        return next;
    }

    public CycleNode getPeer() {
        return peer;
    }
}
