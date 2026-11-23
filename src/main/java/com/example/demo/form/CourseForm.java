package com.example.demo.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CourseForm {

    @NotBlank(message = "课程名称不能为空")
    private String name;

    @NotBlank(message = "教师姓名不能为空")
    private String teacherName;

    @NotNull(message = "请选择开课年份")
    private Integer year;

    @NotBlank(message = "请选择学期")
    private String term;

    @NotNull(message = "请选择授课班级")
    private Long classId;
}
