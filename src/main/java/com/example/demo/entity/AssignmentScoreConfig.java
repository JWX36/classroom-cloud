package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 作业评分方案。
 * mode = EVEN：客观题平均分配 100 分，没有主观题分值；
 * mode = CUSTOM：按题型设置占比（分），剩余 100 - 合计 作为主观题（附件）分值。
 */
@Data
@TableName("assignment_score_config")
public class AssignmentScoreConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String EVEN = "EVEN";
    public static final String CUSTOM = "CUSTOM";

    @TableId(value = "assignment_id", type = IdType.INPUT)
    private Long assignmentId;
    private String mode;
    private BigDecimal singlePercent;
    private BigDecimal multiplePercent;
    private BigDecimal judgePercent;
    /** 附件（主观题）占比；未设置（0/null）时按旧规则取 100 减去各题型合计。 */
    private BigDecimal attachmentPercent;

    public boolean isCustom() {
        return CUSTOM.equalsIgnoreCase(mode);
    }

    public static AssignmentScoreConfig even(Long assignmentId) {
        AssignmentScoreConfig config = new AssignmentScoreConfig();
        config.setAssignmentId(assignmentId);
        config.setMode(EVEN);
        config.setSinglePercent(BigDecimal.ZERO);
        config.setMultiplePercent(BigDecimal.ZERO);
        config.setJudgePercent(BigDecimal.ZERO);
        config.setAttachmentPercent(BigDecimal.ZERO);
        return config;
    }

    public BigDecimal percentOf(String type) {
        BigDecimal value;
        if ("MULTIPLE".equals(type)) value = multiplePercent;
        else if ("JUDGE".equals(type)) value = judgePercent;
        else value = singlePercent;
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 各题型占比合计，也就是客观题总分。 */
    public BigDecimal objectivePercent() {
        return nz(singlePercent).add(nz(multiplePercent)).add(nz(judgePercent));
    }

    /** 附件（主观题）分值：显式设置的附件占比优先，否则退回旧规则（100 减各题型合计）。 */
    public BigDecimal subjectivePercent() {
        if (isCustom()) {
            BigDecimal attach = nz(attachmentPercent);
            if (attach.compareTo(BigDecimal.ZERO) > 0) return attach;
        }
        BigDecimal rest = new BigDecimal("100").subtract(objectivePercent());
        return rest.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : rest;
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
