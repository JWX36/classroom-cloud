package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@TableName("submission")
public class Submission implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assignmentId;
    private Long studentId;
    private String content;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String status;
    private BigDecimal score;
    private String teacherComment;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime gradedAt;
    private Long gradedBy;
    private Boolean published;

    @TableField(exist = false)
    private String studentName;
    @TableField(exist = false)
    private String studentSno;
    @TableField(exist = false)
    private String assignmentTitle;
    @TableField(exist = false)
    private String courseName;

    /** 客观题自动得分（按答案计算）。 */
    @TableField(exist = false)
    private BigDecimal objectiveScore;
    /** 主观题（附件）得分，教师手动录入。 */
    @TableField(exist = false)
    private BigDecimal subjectiveScore;
    /** 本次作业客观题总分。 */
    @TableField(exist = false)
    private BigDecimal objectiveFull;
    /** 本次作业主观题总分 = 100 - 客观题总分。 */
    @TableField(exist = false)
    private BigDecimal subjectiveFull;

    public String getStatusText() {
        if ("GRADED".equals(status)) return "已批阅";
        if ("MISSING".equals(status)) return "未提交";
        return "已提交";
    }

    /** 教师端批改状态：提交了但还没打完分显示“未完成批改”。 */
    public String getGradeStatusText() {
        if ("GRADED".equals(status)) return "已批阅";
        if ("MISSING".equals(status)) return "未提交";
        return "未完成批改";
    }

    public boolean isGradeIncomplete() {
        return "SUBMITTED".equals(status);
    }

    public boolean isNeedsSubjective() {
        return subjectiveFull != null && subjectiveFull.compareTo(BigDecimal.ZERO) > 0;
    }

    public String getObjectiveScoreText() {
        return objectiveScore == null ? "0" : text(objectiveScore);
    }

    public String getSubjectiveScoreText() {
        return subjectiveScore == null ? "" : text(subjectiveScore);
    }

    /** 尚未保存总分时用于预览：客观 + 主观。 */
    public String getPreviewTotalText() {
        if (score != null) return text(score);
        BigDecimal objective = objectiveScore == null ? BigDecimal.ZERO : objectiveScore;
        BigDecimal subjective = subjectiveScore == null ? BigDecimal.ZERO : subjectiveScore;
        return text(objective.add(subjective));
    }

    /** “客观 80 + 主观 15” 形式的来源说明。 */
    public String getScoreBreakdownText() {
        BigDecimal objective = objectiveScore == null ? BigDecimal.ZERO : objectiveScore;
        BigDecimal subjective = subjectiveScore == null ? BigDecimal.ZERO : subjectiveScore;
        if (subjectiveFull != null && subjectiveFull.compareTo(BigDecimal.ZERO) > 0) {
            return "客观 " + text(objective) + " + 主观 " + text(subjective);
        }
        return "客观 " + text(objective);
    }

    private String text(BigDecimal value) {
        if (value == null) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    public String getStatusBadge() {
        if ("GRADED".equals(status)) return "green";
        if ("MISSING".equals(status)) return "gray";
        return "orange";
    }

    public boolean isGraded() {
        return "GRADED".equals(status);
    }

    public boolean isMissing() {
        return "MISSING".equals(status);
    }

    public boolean isPublishedValue() {
        return Boolean.TRUE.equals(published);
    }

    public String getScoreText() {
        if (score == null) return "—";
        return score.stripTrailingZeros().toPlainString();
    }

    public String getSubmittedText() {
        LocalDateTime time = submittedAt == null ? createdAtFallback() : submittedAt;
        return time == null ? "—" : time.format(FMT);
    }

    private LocalDateTime createdAtFallback() {
        return updatedAt;
    }

    public String getFileSizeText() {
        if (fileSize == null) return "";
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        return String.format("%.1f MB", fileSize / 1024.0 / 1024.0);
    }
}
