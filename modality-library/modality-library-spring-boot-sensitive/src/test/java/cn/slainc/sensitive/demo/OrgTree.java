package cn.slainc.sensitive.demo;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class OrgTree {

    private String name = "总部";

    private List<Department> departments = List.of(
            new Department("研发部", new Employee("李四", "13712345678", "lisi@example.com")));

    private Department[] departmentArray = {
            new Department("市场部", new Employee("王五", "13655556666", "wangwu@example.com"))};

    private Set<Employee> members = Set.of(new Employee("赵六", "13566667777", "zhaoliu@example.com"));

    private Map<String, Department> departmentMap = Map.of("research",
            new Department("研究院", new Employee("钱七", "13477778888", "qianqi@example.com")));

    public String getName() {
        return name;
    }

    public List<Department> getDepartments() {
        return departments;
    }

    public Department[] getDepartmentArray() {
        return departmentArray;
    }

    public Set<Employee> getMembers() {
        return members;
    }

    public Map<String, Department> getDepartmentMap() {
        return departmentMap;
    }
}
