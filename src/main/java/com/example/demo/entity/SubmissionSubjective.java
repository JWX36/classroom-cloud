package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 主观题（附件）得分，教师批改时手动录入。 */
@Data
@TableName("submission_subjective")
public class SubmissionSubjective implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "submission_id", type = IdType.INPUT)
    private Long submissionId;
    private BigDecimal score;
    private LocalDateTime updatedAt;
}
