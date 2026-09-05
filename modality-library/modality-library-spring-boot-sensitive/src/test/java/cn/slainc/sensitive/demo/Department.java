package cn.slainc.sensitive.demo;

import java.util.List;

public class Department {

    private String name;

    private Employee owner;

    private List<Employee> members;

    public Department(String name, Employee owner) {
        this.name = name;
        this.owner = owner;
        this.members = List.of(owner, new Employee(name + "成员", "13400001111", "member@example.com"));
    }

    public String getName() {
        return name;
    }

    public Employee getOwner() {
        return owner;
    }

    public List<Employee> getMembers() {
        return members;
    }
}
