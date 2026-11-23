package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String password;
    private String name;
    private String role;
    private String phone;
    private String email;
    private String avatar;
    private Boolean enabled;
    private LocalDateTime createdAt;

    public String getRoleText() {
        if ("ADMIN".equals(role)) return "管理员";
        if ("TEACHER".equals(role)) return "教师";
        return "学生";
    }

    public String getInitial() {
        if (name == null || name.isEmpty()) return "?";
        return name.length() <= 2 ? name : name.substring(0, 1);
    }
}
