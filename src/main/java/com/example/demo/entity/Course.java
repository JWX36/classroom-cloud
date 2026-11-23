package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("course")
public class Course implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long teacherId;
    private String teacherName;
    private Long classId;
    private Integer courseYear;
    private String termName;
    private String status;
    private String rejectReason;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String className;

    public String getTermLabel() {
        return (courseYear == null ? "" : courseYear + "年 ") + (termName == null ? "" : termName);
    }

    public LocalDateTime getEndTime() {
        if (courseYear == null || termName == null) return LocalDateTime.MAX;
        LocalDate end = "上学期".equals(termName)
                ? LocalDate.of(courseYear + 1, 1, 31)
                : LocalDate.of(courseYear, 7, 31);
        return end.atTime(23, 59, 59);
    }

    public boolean isEnded() {
        return "ENDED".equals(status) || LocalDateTime.now().isAfter(getEndTime());
    }

    public boolean isActive() {
        return "APPROVED".equals(status) && !isEnded();
    }

    public String getStatusText() {
        if ("PENDING".equals(status)) return "待审批";
        if ("REJECTED".equals(status)) return "已驳回";
        if ("ENDED".equals(status)) return "已结束";
        return isEnded() ? "已结束" : "进行中";
    }

    public String getStatusBadge() {
        if ("PENDING".equals(status)) return "orange";
        if ("REJECTED".equals(status)) return "red";
        if (isEnded()) return "gray";
        return "green";
    }
}
