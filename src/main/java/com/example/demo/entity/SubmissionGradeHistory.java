package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 成绩历史：一次成绩被覆盖前的归档记录。 */
@Data
@TableName("submission_grade_history")
public class SubmissionGradeHistory implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long submissionId;
    private BigDecimal score;
    private String teacherComment;
    private LocalDateTime gradedAt;
    private Long gradedBy;
    private LocalDateTime attemptSubmittedAt;
    private LocalDateTime createdAt;

    public String getScoreText() {
        return score == null ? "—" : score.stripTrailingZeros().toPlainString();
    }

    public String getGradedText() {
        return gradedAt == null ? "" : gradedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    public String getAttemptText() {
        return attemptSubmittedAt == null ? ""
                : attemptSubmittedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
