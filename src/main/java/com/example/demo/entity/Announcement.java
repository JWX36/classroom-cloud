package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@TableName("announcement")
public class Announcement implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private String title;
    private String content;
    private Boolean pinned;
    private Long createdBy;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String courseName;
    @TableField(exist = false)
    private String teacherName;

    public boolean isPinnedValue() {
        return Boolean.TRUE.equals(pinned);
    }

    public String getCreatedText() {
        return createdAt == null ? "" : createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
