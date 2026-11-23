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
@TableName("assignment")
public class Assignment implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private String title;
    private String requirement;
    private LocalDateTime deadline;
    private String attachmentName;
    private String attachmentPath;
    private Boolean missingMarked;
    private Long createdBy;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String courseName;
    @TableField(exist = false)
    private Submission mySubmission;
    @TableField(exist = false)
    private long submittedCount;
    @TableField(exist = false)
    private long gradedCount;
    @TableField(exist = false)
    private int totalStudents;

    @TableField(exist = false)
    private java.util.List<Question> questions;

    @TableField(exist = false)
    private java.util.Map<Long, String> answers;

    /** 评分方案（EVEN / CUSTOM），见 AssignmentScoreConfig。 */
    @TableField(exist = false)
    private AssignmentScoreConfig scoreConfig;
    /** 客观题总分（题目分值之和）。 */
    @TableField(exist = false)
    private java.math.BigDecimal objectiveFull = new java.math.BigDecimal("100");
    /** 主观题（附件）总分 = 100 - 客观题总分。 */
    @TableField(exist = false)
    private java.math.BigDecimal subjectiveFull = java.math.BigDecimal.ZERO;

    public String getDeadlineText() {
        return deadline == null ? "" : deadline.format(FMT);
    }

    public boolean isExpired() {
        return deadline != null && LocalDateTime.now().isAfter(deadline);
    }

    public boolean isHasAttachment() {
        return attachmentPath != null && !attachmentPath.isBlank();
    }

    public boolean isHasQuestions() {
        return questions != null && !questions.isEmpty();
    }

    public String getObjectiveFullText() {
        return text(objectiveFull);
    }

    public String getSubjectiveFullText() {
        return text(subjectiveFull);
    }

    public boolean isSubjectiveScored() {
        return subjectiveFull != null && subjectiveFull.compareTo(java.math.BigDecimal.ZERO) > 0;
    }

    private String text(java.math.BigDecimal value) {
        if (value == null) return "0";
        return value.stripTrailingZeros().toPlainString();
    }
}
