package cn.slainc.excel;

import cn.idev.excel.annotation.ExcelProperty;
import cn.slainc.excel.annotation.ExcelDict;
import cn.slainc.excel.converter.DictConverter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class UserRow {

    @ExcelProperty("姓名")
    @NotBlank(message = "不能为空")
    private String name;

    @ExcelProperty("年龄")
    @Min(value = 0, message = "不能为负数")
    private Integer age;

    @ExcelProperty(value = "状态", converter = DictConverter.class)
    @ExcelDict("user_status")
    private UserStatus status;

    public UserRow() {
    }

    public UserRow(String name, Integer age, UserStatus status) {
        this.name = name;
        this.age = age;
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }
}
